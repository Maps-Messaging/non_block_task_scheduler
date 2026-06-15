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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public abstract class AbstractConcurrentTaskSchedulerContractTest {

  protected abstract ConcurrentTaskScheduler create();

  @Test
  void shutdownCalledInsideTaskWithQueuedWorkDoesNotDeadlock() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch queuedSubmitted = new CountDownLatch(1);
    CountDownLatch queuedRan = new CountDownLatch(1);

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(queuedSubmitted);
            taskScheduler.shutdown();
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      Future<?> queuedFuture = taskScheduler.submit(queuedRan::countDown);
      queuedSubmitted.countDown();

      assertTrue(queuedRan.await(2, TimeUnit.SECONDS));
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
      assertTrue(queuedFuture.isDone());
      assertFalse(queuedFuture.isCancelled());

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void gracefulShutdownDrainsAcceptedTasksRejectsNewTasksAndTerminates() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    CountDownLatch queuedRan = new CountDownLatch(1);

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      Future<?> queuedFuture = taskScheduler.submit(queuedRan::countDown);

      taskScheduler.shutdown();

      assertTrue(taskScheduler.isShutdown());
      assertThrows(RejectedExecutionException.class, () -> taskScheduler.submit(() -> {
      }));

      releaseRunningTask.countDown();

      assertTrue(queuedRan.await(2, TimeUnit.SECONDS));
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
      assertTrue(taskScheduler.isTerminated());
      assertTrue(queuedFuture.isDone());
      assertFalse(queuedFuture.isCancelled());

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void externalShutdownNowReturnsQueuedTasksAndDoesNotTerminateWhileWorkRuns() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    AtomicInteger queuedRunCount = new AtomicInteger();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      Future<?> queuedFuture1 = taskScheduler.submit(queuedRunCount::incrementAndGet);
      Future<?> queuedFuture2 = taskScheduler.submit(queuedRunCount::incrementAndGet);

      List<Runnable> queuedTasks = taskScheduler.shutdownNow();

      assertEquals(2, queuedTasks.size());
      assertTrue(queuedFuture1.isCancelled());
      assertTrue(queuedFuture2.isCancelled());
      assertEquals(0, queuedRunCount.get());
      assertTrue(taskScheduler.isShutdown());
      assertFalse(taskScheduler.isTerminated());

      releaseRunningTask.countDown();

      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
      assertTrue(taskScheduler.isTerminated());

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void queuedTasksExecuteExactlyOnce() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    CountDownLatch queuedTasksRan = new CountDownLatch(3);
    AtomicInteger runCount = new AtomicInteger();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      taskScheduler.submit(() -> {
        runCount.incrementAndGet();
        queuedTasksRan.countDown();
      });
      taskScheduler.submit(() -> {
        runCount.incrementAndGet();
        queuedTasksRan.countDown();
      });
      taskScheduler.submit(() -> {
        runCount.incrementAndGet();
        queuedTasksRan.countDown();
      });

      releaseRunningTask.countDown();

      assertTrue(queuedTasksRan.await(2, TimeUnit.SECONDS));
      assertEquals(3, runCount.get());

      taskScheduler.shutdown();
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void taskExceptionDoesNotPreventLaterTaskExecution() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    AtomicInteger successCount = new AtomicInteger();

    Future<?> failedFuture = taskScheduler.submit(() -> {
      throw new IllegalStateException("boom");
    });

    Future<?> successfulFuture = taskScheduler.submit(successCount::incrementAndGet);

    ExecutionException executionException = assertThrows(ExecutionException.class, failedFuture::get);
    assertInstanceOf(IllegalStateException.class, executionException.getCause());

    successfulFuture.get(2, TimeUnit.SECONDS);

    assertEquals(1, successCount.get());

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void invokeAllReturnsFuturesAndDoesNotLeakTaskFailures() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Callable<String>> tasks = List.of(
        () -> {
          throw new IllegalStateException("boom");
        },
        () -> "ok"
    );

    List<Future<String>> futures = taskScheduler.invokeAll(tasks);

    assertEquals(2, futures.size());

    ExecutionException executionException = assertThrows(ExecutionException.class, () -> futures.get(0).get());
    assertInstanceOf(IllegalStateException.class, executionException.getCause());

    assertEquals("ok", futures.get(1).get());

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void timedInvokeAllCancelsUnfinishedQueuedTasks() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      List<Callable<String>> tasks = List.of(
          () -> "one",
          () -> "two"
      );

      List<Future<String>> futures = taskScheduler.invokeAll(tasks, 50, TimeUnit.MILLISECONDS);

      assertEquals(2, futures.size());
      assertTrue(futures.get(0).isCancelled());
      assertTrue(futures.get(1).isCancelled());

      releaseRunningTask.countDown();

      taskScheduler.shutdown();
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void invokeAnyReturnsFirstSuccessfulResultAfterFailures() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Callable<String>> tasks = List.of(
        () -> {
          throw new IllegalStateException("first failed");
        },
        () -> "second"
    );

    assertEquals("second", taskScheduler.invokeAny(tasks));

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void invokeAnyThrowsExecutionExceptionWhenAllTasksFail() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Callable<String>> tasks = List.of(
        () -> {
          throw new IllegalStateException("first failed");
        },
        () -> {
          throw new IllegalArgumentException("second failed");
        }
    );

    ExecutionException executionException = assertThrows(ExecutionException.class, () -> taskScheduler.invokeAny(tasks));

    assertInstanceOf(Exception.class, executionException.getCause());

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void timedInvokeAnyTimesOutAndCancelsQueuedTask() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

      List<Callable<String>> tasks = List.of(() -> "late");

      assertThrows(TimeoutException.class, () -> taskScheduler.invokeAny(tasks, 50, TimeUnit.MILLISECONDS));

      releaseRunningTask.countDown();

      taskScheduler.shutdown();
      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void submitAfterShutdownThrowsRejectedExecutionException() {
    ConcurrentTaskScheduler taskScheduler = create();

    taskScheduler.shutdown();

    assertThrows(RejectedExecutionException.class, () -> taskScheduler.submit(() -> {
    }));
  }

  @Test
  void submittedCallableReturnsResult() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    Future<String> future = taskScheduler.submit(() -> "result");

    assertEquals("result", future.get(2, TimeUnit.SECONDS));

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void submittedRunnableReturnsSuppliedResult() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    Future<String> future = taskScheduler.submit(() -> {
    }, "result");

    assertEquals("result", future.get(2, TimeUnit.SECONDS));

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void shutdownNowRejectsNewTasks() {
    ConcurrentTaskScheduler taskScheduler = create();

    taskScheduler.shutdownNow();

    assertThrows(RejectedExecutionException.class, () -> taskScheduler.submit(() -> {
    }));
  }

  @Test
  void shutdownNowOnIdleSchedulerTerminatesImmediately() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Runnable> queuedTasks = taskScheduler.shutdownNow();

    assertTrue(queuedTasks.isEmpty());
    assertTrue(taskScheduler.isShutdown());
    assertTrue(taskScheduler.isTerminated());
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void gracefulShutdownOnIdleSchedulerTerminatesImmediately() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    taskScheduler.shutdown();

    assertTrue(taskScheduler.isShutdown());
    assertTrue(taskScheduler.isTerminated());
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void awaitTerminationReturnsFalseWhenTimeoutExpires() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      awaitLatch(runningStarted);

      taskScheduler.shutdown();

      assertFalse(taskScheduler.awaitTermination(50, TimeUnit.MILLISECONDS));

      releaseRunningTask.countDown();

      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void cancellingQueuedFuturePreventsExecution() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    AtomicInteger runCount = new AtomicInteger();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      awaitLatch(runningStarted);

      Future<?> queuedFuture = taskScheduler.submit(runCount::incrementAndGet);

      assertTrue(queuedFuture.cancel(true));

      releaseRunningTask.countDown();

      taskScheduler.shutdown();

      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
      assertEquals(0, runCount.get());
      assertTrue(queuedFuture.isCancelled());

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void cancelledQueuedTaskStillAllowsLaterTaskToRun() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch runningStarted = new CountDownLatch(1);
    CountDownLatch releaseRunningTask = new CountDownLatch(1);
    CountDownLatch laterTaskRan = new CountDownLatch(1);
    AtomicInteger runCount = new AtomicInteger();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> callerFuture = caller.submit(() ->
          taskScheduler.submit(() -> {
            runningStarted.countDown();
            awaitLatch(releaseRunningTask);
          })
      );

      awaitLatch(runningStarted);

      Future<?> cancelledFuture = taskScheduler.submit(runCount::incrementAndGet);
      Future<?> laterFuture = taskScheduler.submit(() -> {
        runCount.incrementAndGet();
        laterTaskRan.countDown();
      });

      assertTrue(cancelledFuture.cancel(true));

      releaseRunningTask.countDown();

      awaitLatch(laterTaskRan);

      taskScheduler.shutdown();

      assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
      assertEquals(1, runCount.get());
      assertTrue(cancelledFuture.isCancelled());
      assertTrue(laterFuture.isDone());

      callerFuture.get(2, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  void nestedSubmitFromSchedulerThreadRunsWithoutDeadlock() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch nestedRan = new CountDownLatch(1);

    Future<?> outerFuture = taskScheduler.submit(() ->
        taskScheduler.submit(nestedRan::countDown)
    );

    outerFuture.get(2, TimeUnit.SECONDS);
    awaitLatch(nestedRan);

    taskScheduler.shutdown();

    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void invokeAllWithEmptyCollectionReturnsEmptyList() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Future<Object>> futures = taskScheduler.invokeAll(List.of());

    assertTrue(futures.isEmpty());

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void invokeAnyWithEmptyCollectionThrowsIllegalArgumentException() {
    ConcurrentTaskScheduler taskScheduler = create();

    assertThrows(IllegalArgumentException.class, () -> taskScheduler.invokeAny(List.of()));

    taskScheduler.shutdown();
  }

  @Test
  void timedInvokeAnyWithEmptyCollectionThrowsIllegalArgumentException() {
    ConcurrentTaskScheduler taskScheduler = create();

    assertThrows(IllegalArgumentException.class, () -> taskScheduler.invokeAny(List.of(), 1, TimeUnit.SECONDS));

    taskScheduler.shutdown();
  }

  @Test
  void timedInvokeAllWithAlreadyExpiredTimeoutCancelsAllFutures() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    List<Future<String>> futures = taskScheduler.invokeAll(
        List.of(
            () -> "one",
            () -> "two"
        ),
        0,
        TimeUnit.MILLISECONDS
    );

    assertEquals(2, futures.size());
    assertTrue(futures.get(0).isCancelled());
    assertTrue(futures.get(1).isCancelled());

    taskScheduler.shutdown();
    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Test
  void timedInvokeAnyWithAlreadyExpiredTimeoutThrowsTimeoutException() {
    ConcurrentTaskScheduler taskScheduler = create();

    assertThrows(
        TimeoutException.class,
        () -> taskScheduler.invokeAny(List.of(() -> "late"), 0, TimeUnit.MILLISECONDS)
    );

    taskScheduler.shutdown();
  }

  @Test
  void shutdownInsideTaskRejectsNestedSubmitButAllowsAlreadyQueuedWork() throws Exception {
    ConcurrentTaskScheduler taskScheduler = create();

    CountDownLatch queuedRan = new CountDownLatch(1);

    Future<?> firstFuture = taskScheduler.submit(() -> {
      taskScheduler.submit(queuedRan::countDown);

      taskScheduler.shutdown();

      assertThrows(RejectedExecutionException.class, () -> taskScheduler.submit(() -> {
      }));
    });

    firstFuture.get(2, TimeUnit.SECONDS);

    awaitLatch(queuedRan);

    assertTrue(taskScheduler.awaitTermination(2, TimeUnit.SECONDS));
  }

  protected void awaitLatch(CountDownLatch latch) {
    try {
      assertTrue(latch.await(2, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}