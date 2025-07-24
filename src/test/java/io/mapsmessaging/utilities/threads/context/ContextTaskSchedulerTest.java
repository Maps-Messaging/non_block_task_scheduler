/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2025 ] MapsMessaging B.V.
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


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ContextTaskSchedulerTest {

  private ContextTaskScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new ContextTaskScheduler("test-domain");
  }

  @AfterEach
  void tearDown() {
    scheduler.shutdown();
  }

  @Test
  void testSubmitCallable_executesSuccessfully() throws Exception {
    Future<String> future = scheduler.submit(() -> "done");
    assertEquals("done", future.get(1, TimeUnit.SECONDS));
  }

  @Test
  void testSubmitRunnable_executesSuccessfully() throws Exception {
    StringBuilder result = new StringBuilder();
    Future<String> future = scheduler.submit(() -> result.append("run"), "ok");
    assertEquals("ok", future.get(1, TimeUnit.SECONDS));
    assertEquals("run", result.toString());
  }

  @Test
  void testSubmitCallable_afterShutdown_rejected() {
    scheduler.shutdown();
    assertThrows(RejectedExecutionException.class,
        () -> scheduler.submit(() -> "fail"));
  }

  @Test
  void testSubmitRunnable_afterTermination_rejected() {
    scheduler.shutdownNow();
    assertThrows(RejectedExecutionException.class,
        () -> scheduler.submit(() -> {}, "fail"));
  }

  @Test
  void testIsEmpty_returnsTrueWhenNoTasks() {
    assertTrue(scheduler.isEmpty());
  }

  @Test
  void testSubmit_immutableRunnable_setsIsImmutableTrue() throws Exception {
    class MyRunnable implements Runnable, Immutable {
      @Override
      public void run() {}
    }

    Future<?> future = scheduler.submit(new MyRunnable(), "result");

    assertTrue(future instanceof RunnableFuture);

    // Optional: reflectively check isImmutable
    var field = future.getClass().getDeclaredField("isImmutable");
    field.setAccessible(true);
    assertTrue((boolean) field.get(future));
  }
}