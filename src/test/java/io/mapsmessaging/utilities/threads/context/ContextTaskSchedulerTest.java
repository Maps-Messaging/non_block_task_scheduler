/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.mapsmessaging.utilities.threads.context;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mapsmessaging.utilities.threads.tasks.AbstractConcurrentTaskSchedulerContractTest;
import io.mapsmessaging.utilities.threads.tasks.ConcurrentTaskScheduler;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import org.junit.jupiter.api.Test;

class ContextTaskSchedulerTest extends AbstractConcurrentTaskSchedulerContractTest {

  private ContextTaskScheduler scheduler;

  @Override
  protected ConcurrentTaskScheduler create() {
    scheduler = new ContextTaskScheduler("context-test");
    return scheduler;
  }

  @Test
  void submitImmutableRunnableSetsImmutableTrue() throws Exception {
    create();

    class MyRunnable implements Runnable, Immutable {

      @Override
      public void run() {
      }
    }

    Future<?> future = scheduler.submit(new MyRunnable(), "result");

    assertTrue(future instanceof RunnableFuture);

    var field = future.getClass().getDeclaredField("immutable");
    field.setAccessible(true);

    assertTrue((boolean) field.get(future));

    scheduler.shutdown();
    assertTrue(scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS));
  }

  @Test
  void submitImmutableCallableSetsImmutableTrue() throws Exception {
    create();

    class MyCallable implements java.util.concurrent.Callable<String>, Immutable {

      @Override
      public String call() {
        return "result";
      }

      @Override
      public void run() {
      }
    }

    java.util.concurrent.Callable<String> callable = new MyCallable();

    Future<?> future = scheduler.submit(callable);

    assertTrue(future instanceof RunnableFuture);

    var field = future.getClass().getDeclaredField("immutable");
    field.setAccessible(true);

    assertTrue((boolean) field.get(future));

    scheduler.shutdown();
    assertTrue(scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS));
  }
}