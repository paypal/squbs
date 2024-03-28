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

package org.squbs.pattern.timeoutpolicy;

import org.apache.pekko.actor.ActorSystem;
import org.squbs.util.DurationConverters;

import java.time.Duration;
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
    private Callable<Duration> timedCall = () -> {
        // 100 ms +- 50 ms randomize offset
        int waitTime = (int) (Math.random() * 100 + 50);
        Thread.sleep(waitTime);
        return Duration.ofMillis(waitTime);
    };
    private TimedFn<Duration> timedFn = (Duration t) -> {
        // offset 20 ms on top of what's set in the timeout policy
        // empirical policies will skew left due to timeout cut-off at the right-side tail
        // offset will allow the sigma to breathe for the future.get()
        return es.submit(timedCall).get(t.toMillis() + 20, MILLISECONDS);
    };

    public TimeoutPolicyJ() {
        fixedTimeoutPolicy = TimeoutPolicyBuilder
                .create(Duration.ofMillis(INITIAL_TIMEOUT), fromExecutorService(es))
                .minSamples(1)
                .rule(TimeoutPolicyType.FIXED)
                .build();
        sigmaTimeoutPolicy = TimeoutPolicyBuilder
                .create(Duration.ofMillis(INITIAL_TIMEOUT), system.dispatcher())
                .minSamples(1)
                .name("SIGMA")
                .rule(3.0, TimeoutPolicyType.SIGMA)
                .build();
        percentileTimeoutPolicy = TimeoutPolicyBuilder
                .create(Duration.ofMillis(INITIAL_TIMEOUT), system.dispatcher())
                .minSamples(1)
                .name("PERCENTILE")
                .rule(95, TimeoutPolicyType.PERCENTILE)
                .build();
    }

    public Duration invokeExecute(TimeoutPolicy policy) {
        try {
            return policy.execute(timedFn);
        } catch (Exception e) {
            System.out.println(e);
        }
        return DurationConverters.toJava(policy.waitTime());
    }

    public Duration invokeExecuteClosure(TimeoutPolicy policy) {
        try {
            // casting (TimedFn<FiniteDuration>) is not required by the compiler, but intelliJ needs it
            return policy.execute((TimedFn<Duration>) (Duration t) -> {
                return es.submit(timedCall).get(t.toMillis() + 20, MILLISECONDS);
            });
        } catch (Exception e) {
            System.out.println(e);
        }
        return DurationConverters.toJava(policy.waitTime());
    }

    public Duration invokeInline(TimeoutPolicy policy) {
        TimeoutPolicy.TimeoutTransaction tx = policy.transaction();
        try {
            return timedFn.get(DurationConverters.toJava(tx.waitTime()));
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            tx.end();
        }
        return DurationConverters.toJava(policy.waitTime());
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
