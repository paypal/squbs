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
package org.squbs.marshallers.json;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.marshallers.MarshalUnmarshal;

import static org.junit.Assert.assertEquals;
import static org.squbs.marshallers.json.TestData.*;
import static org.squbs.marshallers.json.XLangJsonSupport.marshaller;
import static org.squbs.marshallers.json.XLangJsonSupport.unmarshaller;

public class XLangJsonTest {

    private static final ActorSystem system = ActorSystem.create("XLangJsonTest");
    private static final Materializer mat = ActorMaterializer.create(system);
    private static final MarshalUnmarshal mu = new MarshalUnmarshal(system.dispatcher(), mat);

    @AfterClass
    public static void afterAll() {
        system.terminate();
    }

    @Test
    public void marshalUnmarshalCaseClass() throws Exception {
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, fullTeamJson());
        RequestEntity content = mu.apply(marshaller(Team.class), fullTeam()).toCompletableFuture().get();
        assertEquals(entity, content);
        Team team = mu.apply(unmarshaller(Team.class), entity).toCompletableFuture().get();
        assertEquals(fullTeam(), team);
    }

    @Test
    public void marshalUnmarshalScalaClass() throws Exception {
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, fullTeamJson());
        RequestEntity content = mu.apply(marshaller(TeamNonCaseClass.class), fullTeamNonCaseClass())
                .toCompletableFuture().get();
        assertEquals(entity, content);
        TeamNonCaseClass team = mu.apply(unmarshaller(TeamNonCaseClass.class), entity).toCompletableFuture().get();
        assertEquals(fullTeamNonCaseClass(), team);
    }

    @Test
    public void marshalUnmarshalScalaClassWithJavaBeans() throws Exception {
        XLangJsonSupport.addSerializers(TeamWithBeanMember.class, EmployeeBeanSerializer.getInstance());
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, fullTeamJson());
        RequestEntity content = mu.apply(marshaller(TeamWithBeanMember.class), fullTeamWithBeanMember())
                .toCompletableFuture().get();
        assertEquals(entity, content);
        TeamWithBeanMember team = mu.apply(unmarshaller(TeamWithBeanMember.class), entity).toCompletableFuture().get();
        assertEquals(fullTeamWithBeanMember(), team);
    }

    @Test
    public void marshalUnmarshalJavaBeanWithCaseClass() throws Exception {
        XLangJsonSupport.register(TeamBeanWithCaseClassMember.class,
                new ObjectMapper().registerModule(new DefaultScalaModule()));
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, fullTeamJson());
        RequestEntity content = mu.apply(marshaller(TeamBeanWithCaseClassMember.class), fullTeamWithCaseClassMember())
                .toCompletableFuture().get();
        assertEquals(entity, content);
        TeamBeanWithCaseClassMember team = mu.apply(unmarshaller(TeamBeanWithCaseClassMember.class), entity)
                .toCompletableFuture().get();
        assertEquals(fullTeamWithCaseClassMember(), team);
    }

    @Test
    public void marshalUnmarshalJavaBean() throws Exception {
        XLangJsonSupport.register(TeamWithPrivateMembers.class,
                new ObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY));
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, fullTeamJson());
        RequestEntity content = mu.apply(marshaller(TeamWithPrivateMembers.class), fullTeamWithPrivateMembers())
                .toCompletableFuture().get();
        assertEquals(entity, content);
        //TeamWithPrivateMembers team = mu.apply(unmarshaller(TeamWithPrivateMembers.class), entity).toCompletableFuture().get();
        //assertEquals(fullTeamWithPrivateMembers(), team);
    }

    @Test
    public void marshalUnmarshalAnnotatedJavaSubclass() throws Exception {
        HttpEntity entity = HttpEntities.create(ContentTypes.APPLICATION_JSON, pageTestJson());
        RequestEntity content = mu.apply(marshaller(PageData.class), pageTest()).toCompletableFuture().get();
        assertEquals(entity, content);
        PageData pageData = mu.apply(unmarshaller(PageData.class), entity).toCompletableFuture().get();
        assertEquals(pageTest(), pageData);
    }
}
