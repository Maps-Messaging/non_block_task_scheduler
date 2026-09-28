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

package io.mapsmessaging.utilities.threads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimpleTaskSchedulerTest {

  private final SimpleTaskScheduler scheduler = SimpleTaskScheduler.getInstance();

  @Test
  void singletonAndDirectExecution() throws Exception {
    assertSame(scheduler, SimpleTaskScheduler.getInstance());

    Future<Integer> callable = scheduler.submit(() -> 42);
    Future<String> runnableResult = scheduler.submit(() -> { }, "done");
    Future<?> runnable = scheduler.submit(() -> { });

    assertEquals(42, callable.get(2, TimeUnit.SECONDS));
    assertEquals("done", runnableResult.get(2, TimeUnit.SECONDS));
    runnable.get(2, TimeUnit.SECONDS);

    CountDownLatch executed = new CountDownLatch(1);
    scheduler.execute(executed::countDown);
    assertTrue(executed.await(2, TimeUnit.SECONDS));
  }

  @Test
  void scheduledTasksExecute() throws Exception {
    CountDownLatch runnableExecuted = new CountDownLatch(1);
    ScheduledFuture<?> runnable = scheduler.schedule(runnableExecuted::countDown, 1, TimeUnit.MILLISECONDS);
    ScheduledFuture<Integer> callable = scheduler.schedule(() -> 7, 1, TimeUnit.MILLISECONDS);

    assertTrue(runnableExecuted.await(2, TimeUnit.SECONDS));
    runnable.get(2, TimeUnit.SECONDS);
    assertEquals(7, callable.get(2, TimeUnit.SECONDS));
  }

  @Test
  void periodicTasksExecuteAndCanBeCancelled() throws Exception {
    CountDownLatch fixedRateRuns = new CountDownLatch(2);
    ScheduledFuture<?> fixedRate =
        scheduler.scheduleAtFixedRate(fixedRateRuns::countDown, 0, 1, TimeUnit.MILLISECONDS);

    CountDownLatch fixedDelayRuns = new CountDownLatch(2);
    ScheduledFuture<?> fixedDelay =
        scheduler.scheduleWithFixedDelay(fixedDelayRuns::countDown, 0, 1, TimeUnit.MILLISECONDS);

    assertTrue(fixedRateRuns.await(2, TimeUnit.SECONDS));
    assertTrue(fixedDelayRuns.await(2, TimeUnit.SECONDS));
    assertTrue(fixedRate.cancel(false));
    assertTrue(fixedDelay.cancel(false));
  }

  @Test
  void bulkOperationsReturnResults() throws Exception {
    List<Callable<Integer>> tasks = List.of(() -> 1, () -> 2, () -> 3);

    List<Future<Integer>> all = scheduler.invokeAll(tasks);
    assertEquals(List.of(1, 2, 3), List.of(all.get(0).get(), all.get(1).get(), all.get(2).get()));

    List<Future<Integer>> timed = scheduler.invokeAll(tasks, 2, TimeUnit.SECONDS);
    assertEquals(List.of(1, 2, 3), List.of(timed.get(0).get(), timed.get(1).get(), timed.get(2).get()));

    assertTrue(List.of(1, 2, 3).contains(scheduler.invokeAny(tasks)));
    assertTrue(List.of(1, 2, 3).contains(scheduler.invokeAny(tasks, 2, TimeUnit.SECONDS)));
  }

  @Test
  void interceptedTasksDelegateAndStatisticsAdvance() throws Exception {
    AtomicInteger value = new AtomicInteger();

    SimpleTaskScheduler.InterceptedRunnable runnable =
        new SimpleTaskScheduler.InterceptedRunnable(value::incrementAndGet);
    runnable.run();

    SimpleTaskScheduler.InterceptedCallable<Integer> callable =
        new SimpleTaskScheduler.InterceptedCallable<>(value::incrementAndGet);

    assertEquals(1, value.get());
    assertEquals(2, callable.call());

    long scheduledBefore = scheduler.getTotalScheduled();
    long executedBefore = scheduler.getTotalExecuted();

    Future<?> future = scheduler.submit(() -> { });
    future.get(2, TimeUnit.SECONDS);

    assertTrue(scheduler.getTotalScheduled() > scheduledBefore);

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (scheduler.getTotalExecuted() <= executedBefore && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertTrue(scheduler.getTotalExecuted() > executedBefore);
    assertTrue(scheduler.getDepth() >= 0);
    assertFalse(scheduler.isShutdown());
    assertFalse(scheduler.isTerminated());
  }
}
