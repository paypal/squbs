/*
 *  Copyright 2015 PayPal
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

package org.squbs.pattern.timeoutpolicy;

import akka.actor.ActorSystem;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static scala.compat.java8.FutureConverters.fromExecutorService;

public class TimeoutPolicyJ {
    static final int INITIAL_TIMEOUT = 300;
    private static final ActorSystem system = ActorSystem.create();
    private static final ExecutorService es = Executors.newCachedThreadPool();
    private final TimeoutPolicy fixedTimeoutPolicy;
    private final TimeoutPolicy sigmaTimeoutPolicy;
    private final TimeoutPolicy percentileTimeoutPolicy;
    // callable randomized duration between 50 ms to 150 ms
    private Callable<FiniteDuration> timedCall = () -> {
        // 100 ms +- 50 ms randomize offset
        int waitTime = (int) (Math.random() * 100 + 50);
        Thread.sleep(waitTime);
        return new FiniteDuration(waitTime, MILLISECONDS);
    };
    private TimedFn<FiniteDuration> timedFn = (FiniteDuration t) -> {
        // offset 20 ms on top of what's set in the timeout policy
        // empirical policies will skew left due to timeout cut-off at the right-side tail
        // offset will allow the sigma to breathe for the future.get()
        return es.submit(timedCall).get(t.toMillis() + 20, MILLISECONDS);
    };

    public TimeoutPolicyJ() {
        fixedTimeoutPolicy = TimeoutPolicyBuilder
                .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), fromExecutorService(es))
                .minSamples(1)
                .rule(TimeoutPolicyType.FIXED)
                .build();
        sigmaTimeoutPolicy = TimeoutPolicyBuilder
                .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), system.dispatcher())
                .minSamples(1)
                .name("SIGMA")
                .rule(3.0, TimeoutPolicyType.SIGMA)
                .build();
        percentileTimeoutPolicy = TimeoutPolicyBuilder
                .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), system.dispatcher())
                .minSamples(1)
                .name("PERCENTILE")
                .rule(95, TimeoutPolicyType.PERCENTILE)
                .build();
    }

    public FiniteDuration invokeExecute(TimeoutPolicy policy) {
        try {
            return policy.execute(timedFn);
        } catch (Exception e) {
            System.out.println(e);
        }
        return policy.waitTime();
    }

    public FiniteDuration invokeExecuteClosure(TimeoutPolicy policy) {
        try {
            // casting (TimedFn<FiniteDuration>) is not required by the compiler, but intelliJ needs it
            return policy.execute((TimedFn<FiniteDuration>) (FiniteDuration t) -> {
                return es.submit(timedCall).get(t.toMillis() + 20, MILLISECONDS);
            });
        } catch (Exception e) {
            System.out.println(e);
        }
        return policy.waitTime();
    }

    public FiniteDuration invokeInline(TimeoutPolicy policy) {
        TimeoutPolicy.TimeoutTransaction tx = policy.transaction();
        try {
            return timedFn.get(tx.waitTime());
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            tx.end();
        }
        return policy.waitTime();
    }

    public TimeoutPolicy getFixedTimeoutPolicy() {
        return fixedTimeoutPolicy;
    }

    public TimeoutPolicy getSigmaTimeoutPolicy() {
        return sigmaTimeoutPolicy;
    }

    public TimeoutPolicy getPercentileTimeoutPolicy() {
        return percentileTimeoutPolicy;
    }

    public void resetTimeoutPolicy(TimeoutPolicy policy) {
        policy.reset();
    }
}