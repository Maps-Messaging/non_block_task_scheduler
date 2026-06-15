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

package io.mapsmessaging.utilities.threads.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PriorityConcurrentTaskSchedulerTest extends AbstractConcurrentTaskSchedulerContractTest {

  @Override
  protected ConcurrentTaskScheduler create() {
    return new PriorityConcurrentTaskScheduler("priority-test", 3);
  }

  @Test
  void constructorRejectsInvalidPrioritySize() {
    assertThrows(IllegalArgumentException.class, () -> new PriorityConcurrentTaskScheduler("priority-test", 0));
    assertThrows(IllegalArgumentException.class, () -> new PriorityConcurrentTaskScheduler("priority-test", -1));
  }

  @Test
  void submitRejectsInvalidPriorityValues() {
    PriorityConcurrentTaskScheduler taskScheduler = new PriorityConcurrentTaskScheduler("priority-test", 3);

    assertThrows(IllegalArgumentException.class, () -> taskScheduler.submit(() -> "bad", -1));
    assertThrows(IllegalArgumentException.class, () -> taskScheduler.submit(() -> "bad", 3));

    taskScheduler.shutdown();
  }

  @Test
  void submitWithPriorityRejectsAfterShutdown() {
    PriorityConcurrentTaskScheduler taskScheduler = new PriorityConcurrentTaskScheduler("priority-test", 3);

    taskScheduler.shutdown();

    assertThrows(RejectedExecutionException.class, () -> taskScheduler.submit(() -> "rejected", 1));
  }

  @Test
  void queuedTasksRunInPriorityOrder() throws Exception {
    PriorityConcurrentTaskScheduler taskScheduler = new PriorityConcurrentTaskScheduler("priority-test", 3);

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    CountDownLatch queuedTasksRan = new CountDownLatch(3);
    List<Integer> order = new CopyOnWriteArrayList<>();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            assertTrue(releaseRunningTask.await(2, TimeUnit.SECONDS));
            return null;
          }, 0)
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      taskScheduler.submit(() -> {
        order.add(2);
        queuedTasksRan.countDown();
        return null;
      }, 2);

      taskScheduler.submit(() -> {
        order.add(1);
        queuedTasksRan.countDown();
        return null;
      }, 1);

      taskScheduler.submit(() -> {
        order.add(0);
        queuedTasksRan.countDown();
        return null;
      }, 0);

      releaseRunningTask.countDown();

      assertTrue(queuedTasksRan.await(2, TimeUnit.SECONDS));
      assertEquals(List.of(0, 1, 2), order);

      taskScheduler.shutdown();
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }
}