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

package io.mapsmessaging.utilities.threads.tasks;

import io.mapsmessaging.utilities.threads.logging.ThreadLoggingMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class implements a ConcurrentTaskScheduler with a priority based concurrent queue. This enables tasks with a higher priority to
 * take precedence over tasks with a lower priority.
 *
 * @since 1.0
 * @author Matthew Buckton
 * @version 2.0
 */
@ToString
public class PriorityConcurrentTaskScheduler extends ConcurrentTaskScheduler implements PriorityTaskScheduler {

  private final List<Queue<FutureTask<?>>> queues;

  /**
   * Constructs the concurrent priority queue, specifying the depth of the priority and the unique domain name that this task queue manages.
   *
   * @param domain a unique domain name
   * @param prioritySize the number of unique priority levels
   */
  public PriorityConcurrentTaskScheduler(@NonNull @NotNull String domain, int prioritySize) {
    super(domain);

    if (prioritySize <= 0) {
      throw new IllegalArgumentException("Priority size must be greater than zero");
    }

    queues = new ArrayList<>();
    for (int index = 0; index < prioritySize; index++) {
      queues.add(new ConcurrentLinkedQueue<>());
    }

    logger.log(ThreadLoggingMessages.PRIORITY_CREATION, domain, prioritySize);
  }

  @Override
  protected <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task) {
    return addTask(task, 0);
  }

  protected <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task, int priority) {
    validatePriority(priority);

    boolean runnerRequired = reserveTaskSlot();

    boolean queued = false;
    try {
      logger.log(ThreadLoggingMessages.PRIORITY_SUBMIT, task.getClass().getName(), priority);
      queues.get(priority).add(task);
      queued = true;
      executeReservedTaskSlot(runnerRequired);
      return task;
    } finally {
      if (!queued) {
        releaseReservedTaskSlot();
      }
    }
  }

  public <T> Future<T> submit(@NonNull @NotNull Callable<T> task, int priority) {
    rejectIfShutdown();
    validatePriority(priority);

    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());

    return addTask(new FutureTask<>(task), priority);
  }

  @Override
  public boolean isEmpty() {
    for (Queue<FutureTask<?>> queue : queues) {
      if (!queue.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected @Nullable FutureTask<?> poll() {
    for (Queue<FutureTask<?>> queue : queues) {
      FutureTask<?> task = queue.poll();
      if (task != null) {
        return task;
      }
    }
    return null;
  }

  private void validatePriority(int priority) {
    if (priority < 0 || priority >= queues.size()) {
      throw new IllegalArgumentException("Priority must be between 0 and " + (queues.size() - 1));
    }
  }
}