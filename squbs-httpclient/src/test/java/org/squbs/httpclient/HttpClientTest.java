/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.httpclient;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.HostConnectionPool;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.resolver.ResolverRegistry;
import org.squbs.httpclient.dummy.DummyServiceJavaTest;
import org.squbs.marshallers.MarshalUnmarshal;
import org.squbs.marshallers.json.*;
import scala.concurrent.Await;
import scala.util.Try;

import java.util.concurrent.CompletionStage;

import static org.apache.pekko.http.javadsl.model.HttpRequest.*;
import static org.junit.Assert.assertEquals;
import static org.squbs.marshallers.json.XLangJsonSupport.marshaller;
import static org.squbs.marshallers.json.XLangJsonSupport.unmarshaller;
import static org.squbs.testkit.Timeouts.awaitMax;

public class HttpClientTest {

    private static final ActorSystem system = ActorSystem.create("HttpClientTest");
    private static final ActorMaterializer mat = ActorMaterializer.create(system);
    private static final DummyServiceJavaTest dummyService = new DummyServiceJavaTest();
    private static final String baseUrl;

    private static final int port;

    static {
        int myPort;
        try {
            myPort = (Integer) Await.result(dummyService.startService(system), awaitMax());
        } catch (Exception e) {
            myPort = -1;
        }
        port = myPort;
        baseUrl = "http://localhost:" + port;
        ResolverRegistry.get(system).register(HttpEndpoint.class, new JavaEndpointResolver(baseUrl));
        ResolverRegistry.get(system).register(HttpEndpoint.class, new JavaEndpointResolver2(baseUrl));
    }

    private static final MarshalUnmarshal um = new MarshalUnmarshal(system.dispatcher(), mat);

    private static final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool>
            clientFlow = ClientFlow.create("DummyService", system, mat);

    private CompletionStage<Try<HttpResponse>> doRequest(HttpRequest request) {
        return Source
                .single(Pair.create(request, 42))
                .via(clientFlow)
                .runWith(Sink.head(), mat)
                .thenApply(Pair::first);
    }

    private static final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool>
            clientFlow2 = ClientFlow.create("DummyService2", system, mat);

    private CompletionStage<Try<HttpResponse>> doRequest2(HttpRequest request) {
        return Source
                .single(Pair.create(request, 42))
                .via(clientFlow2)
                .runWith(Sink.head(), mat)
                .thenApply(Pair::first);
    }

    @AfterClass
    public static void afterAll() {
        system.terminate();
    }

    @Test
    public void get() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest(GET("/view"));
        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals(TestData.fullTeamJson(), content);
    }

    @Test
    public void postEmpty() throws Exception {
        HttpRequest request = POST("/view")
                .addHeader(RawHeader.create("req1-name", "test123456"))
                .addHeader(RawHeader.create("req2-name", "test34567"));
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest2(request);
        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String hdr1v = response.getHeader("res-req1-name").map(HttpHeader::value).orElse("");
        assertEquals("res-test123456", hdr1v);
        String hdr2v = response.getHeader("res-req2-name").map(HttpHeader::value).orElse("");
        assertEquals("res2-test34567", hdr2v);
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals(TestData.fullTeamJson(), content);
    }

    @Test
    public void clientUnmarshalCaseClass() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF = doRequest(GET("/view"));
        CompletionStage<Team> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(Team.class), t.get().entity()));
        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        Team team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeam(), team);
    }

    /*@Test
    public void clientUnmarshalJavaBean() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF = doRequest2(GET("/viewj"));
        CompletionStage<TeamWithPrivateMembers> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(TeamWithPrivateMembers.class), t.get().entity()));
        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        TeamWithPrivateMembers team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeamWithPrivateMembers(), team);

    }*/

    @Test
    public void clientUnmarshalJavaBeanWithCaseClass() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF = doRequest(GET("/view3"));
        CompletionStage<TeamBeanWithCaseClassMember> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(TeamBeanWithCaseClassMember.class), t.get().entity()));
        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        TeamBeanWithCaseClassMember team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeamWithCaseClassMember(), team);

    }

    @Test
    public void clientUnmarshalJavaBeanWithSimpleScalaClass() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF = doRequest2(GET("/view1"));
        CompletionStage<TeamNonCaseClass> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(TeamNonCaseClass.class), t.get().entity()));
        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        TeamNonCaseClass team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeamNonCaseClass(), team);
    }

    @Test
    public void getEmpty() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest(GET("/emptyresponse"));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.NO_CONTENT, response.status());
    }

    @Test
    public void head() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest2(HEAD("/view"));
        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals("", content);
    }

    @Test
    public void options() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest(OPTIONS("/view"));
        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals(TestData.fullTeamJson(), content);
    }

    @Test
    public void optionsUnmarshal() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF = doRequest2(OPTIONS("/view"));
        CompletionStage<Team> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(Team.class), t.get().entity()));
        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        Team team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeam(), team);
    }

    @Test
    public void delete() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse = doRequest(DELETE("/del/4"));
        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));
        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals(TestData.fullTeamWithDelJson(), content);
    }

    @Test
    public void postWithMarshal() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponse =
                um.apply(marshaller(Employee.class), TestData.newTeamMember())
                        .thenApply(entity -> POST("/add").withEntity(entity))
                        .thenCompose(this::doRequest2);

        CompletionStage<HttpEntity.Strict> entity =
                tryResponse.thenCompose(t -> t.get().entity().toStrict(30000L, mat));

        HttpResponse response = tryResponse.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = entity.toCompletableFuture().get().getData().utf8String();
        assertEquals(TestData.fullTeamWithAddJson(), content);
    }

/*    @Test
    public void postWithMarshalUnmarshal() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF =
                um.apply(marshaller(EmployeeBean.class), TestData.newTeamMemberBean())
                        .thenApply(entity -> POST("/addj").withEntity(entity))
                        .thenCompose(this::doRequest);

        CompletionStage<TeamWithPrivateMembers> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(TeamWithPrivateMembers.class), t.get().entity()));

        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        TeamWithPrivateMembers team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeamPrivateMembersWithAdd(), team);
    }*/

    @Test
    public void putWithMarshalUnmarshal() throws Exception {
        CompletionStage<Try<HttpResponse>> tryResponseF =
                um.apply(marshaller(Employee.class), TestData.newTeamMember())
                        .thenApply(entity -> PUT("/add").withEntity(entity))
                        .thenCompose(this::doRequest2);

        CompletionStage<Team> teamF = tryResponseF.thenCompose(t ->
                um.apply(unmarshaller(Team.class), t.get().entity()));

        HttpResponse response = tryResponseF.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        Team team = teamF.toCompletableFuture().get();
        assertEquals(TestData.fullTeamWithAdd(), team);
    }
}
