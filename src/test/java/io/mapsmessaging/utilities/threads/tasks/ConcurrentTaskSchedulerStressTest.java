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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


class ConcurrentTaskSchedulerStressTest {

  static List<Callable<ConcurrentTaskScheduler>> schedulers() {
    return List.of(
        () -> new SingleConcurrentTaskScheduler("stress-single"),
        () -> new PriorityConcurrentTaskScheduler("stress-priority", 4)
    );
  }

  @ParameterizedTest
  @MethodSource("schedulers")
  void concurrentSubmissionsExecuteExactlyOnce(Callable<ConcurrentTaskScheduler> schedulerFactory) throws Exception {
    ConcurrentTaskScheduler taskScheduler = schedulerFactory.call();

    int submitterCount = 8;
    int tasksPerSubmitter = 250;
    int expectedTaskCount = submitterCount * tasksPerSubmitter;

    ExecutorService submitters = Executors.newFixedThreadPool(submitterCount);
    CyclicBarrier startBarrier = new CyclicBarrier(submitterCount);
    CountDownLatch finishedTasks = new CountDownLatch(expectedTaskCount);
    AtomicInteger runCount = new AtomicInteger();

    List<Future<?>> submitterFutures = new ArrayList<>();

    try {
      for (int submitterIndex = 0; submitterIndex < submitterCount; submitterIndex++) {
        submitterFutures.add(submitters.submit(() -> {
          startBarrier.await();

          for (int taskIndex = 0; taskIndex < tasksPerSubmitter; taskIndex++) {
            taskScheduler.submit(() -> {
              runCount.incrementAndGet();
              finishedTasks.countDown();
            });
          }

          return null;
        }));
      }

      for (Future<?> submitterFuture : submitterFutures) {
        submitterFuture.get(5, TimeUnit.SECONDS);
      }

      assertTrue(finishedTasks.await(5, TimeUnit.SECONDS));
      assertEquals(expectedTaskCount, runCount.get());

      taskScheduler.shutdown();

      assertTrue(taskScheduler.awaitTermination(5, TimeUnit.SECONDS));
      assertEquals(0, taskScheduler.getOutstanding());
      assertTrue(taskScheduler.getMaxOutstanding() >= 1);
    } finally {
      submitters.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("schedulers")
  void shutdownRacingWithSubmissionsDoesNotDeadlockOrCorruptOutstanding(
      Callable<ConcurrentTaskScheduler> schedulerFactory
  ) throws Exception {
    ConcurrentTaskScheduler taskScheduler = schedulerFactory.call();

    int submitterCount = 8;
    int tasksPerSubmitter = 250;

    ExecutorService submitters = Executors.newFixedThreadPool(submitterCount);
    CyclicBarrier startBarrier = new CyclicBarrier(submitterCount + 1);
    AtomicInteger acceptedTasks = new AtomicInteger();
    AtomicInteger rejectedTasks = new AtomicInteger();
    AtomicInteger executedTasks = new AtomicInteger();

    List<Future<?>> submitterFutures = new ArrayList<>();

    try {
      for (int submitterIndex = 0; submitterIndex < submitterCount; submitterIndex++) {
        submitterFutures.add(submitters.submit(() -> {
          startBarrier.await();

          for (int taskIndex = 0; taskIndex < tasksPerSubmitter; taskIndex++) {
            try {
              taskScheduler.submit(executedTasks::incrementAndGet);
              acceptedTasks.incrementAndGet();
            } catch (RejectedExecutionException exception) {
              rejectedTasks.incrementAndGet();
            }
          }

          return null;
        }));
      }

      startBarrier.await();

      taskScheduler.shutdown();

      for (Future<?> submitterFuture : submitterFutures) {
        submitterFuture.get(5, TimeUnit.SECONDS);
      }

      assertTrue(taskScheduler.awaitTermination(5, TimeUnit.SECONDS));
      assertEquals(acceptedTasks.get(), executedTasks.get());
      assertTrue(rejectedTasks.get() >= 0);
      assertEquals(0, taskScheduler.getOutstanding());
    } finally {
      submitters.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("schedulers")
  void shutdownNowRacingWithSubmissionsDoesNotDeadlockOrCorruptOutstanding(
      Callable<ConcurrentTaskScheduler> schedulerFactory
  ) throws Exception {
    ConcurrentTaskScheduler taskScheduler = schedulerFactory.call();

    int submitterCount = 8;
    int tasksPerSubmitter = 250;

    ExecutorService submitters = Executors.newFixedThreadPool(submitterCount);
    CyclicBarrier startBarrier = new CyclicBarrier(submitterCount + 1);
    AtomicInteger completedTasks = new AtomicInteger();
    AtomicInteger rejectedTasks = new AtomicInteger();

    List<Future<?>> submitterFutures = new ArrayList<>();

    try {
      for (int submitterIndex = 0; submitterIndex < submitterCount; submitterIndex++) {
        submitterFutures.add(submitters.submit(() -> {
          startBarrier.await();

          for (int taskIndex = 0; taskIndex < tasksPerSubmitter; taskIndex++) {
            try {
              taskScheduler.submit(completedTasks::incrementAndGet);
            } catch (RejectedExecutionException exception) {
              rejectedTasks.incrementAndGet();
            }
          }

          return null;
        }));
      }

      startBarrier.await();

      List<Runnable> returnedTasks = taskScheduler.shutdownNow();

      for (Future<?> submitterFuture : submitterFutures) {
        submitterFuture.get(5, TimeUnit.SECONDS);
      }

      assertTrue(taskScheduler.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(returnedTasks.size() >= 0);
      assertTrue(completedTasks.get() >= 0);
      assertTrue(rejectedTasks.get() >= 0);
      assertEquals(0, taskScheduler.getOutstanding());
    } finally {
      submitters.shutdownNow();
    }
  }
}