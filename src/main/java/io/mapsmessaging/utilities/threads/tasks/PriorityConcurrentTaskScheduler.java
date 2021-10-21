/*
 *
 *   Copyright [ 2020 - 2021 ] [Matthew Buckton]
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.mapsmessaging.utilities.threads.tasks;

import io.mapsmessaging.logging.Logger;
import io.mapsmessaging.logging.LoggerFactory;
import io.mapsmessaging.utilities.threads.logging.ThreadLoggingMessages;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;

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
   * Constructs the concurrent priority queue, specifying the depth of the priority and the unique domain name that this task queue manages
   *
   * @param domain  a unique domain name
   * @param prioritySize the number of unique priority levels
   */
  public PriorityConcurrentTaskScheduler(@NonNull @NotNull String domain, int prioritySize) {
    super(domain);
    queues = new ArrayList<>();
    for(var x=0;x<prioritySize;x++){
      queues.add(new ConcurrentLinkedQueue<>());
    }
    logger.log(ThreadLoggingMessages.PRIORITY_CREATION, domain, prioritySize);
  }

  @Override
  protected <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task) {
    return addTask(task, 0);
  }

  protected <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task, int priority) {
    if(!shutdown) {
      logger.log(ThreadLoggingMessages.PRIORITY_SUBMIT, task.getClass().getName(), priority);
      queues.get(priority).add(task);
      executeQueue();
    }
    else{
      logger.log(ThreadLoggingMessages.SCHEDULER_SHUTDOWN, task.getClass().getName());
      task.cancel(true);
    }
    return task;
  }

  public <T> Future<T> submit(@NonNull @NotNull Callable<T> task, int priority) {
    return addTask(new FutureTask<>(task), priority);
  }

  @Override
  public boolean isEmpty(){
    for(Queue<FutureTask<?>> queue:queues){
      if(!queue.isEmpty()){
        return false;
      }
    }
    return true;
  }

  @Override
  protected @Nullable FutureTask<?> poll(){
    for(Queue<FutureTask<?>> queue:queues){
      FutureTask<?> task = queue.poll();
      if(task != null){
        return task;
      }
    }
    return null;
  }

}


