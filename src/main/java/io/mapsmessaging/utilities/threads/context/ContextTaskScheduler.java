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

import io.mapsmessaging.utilities.threads.logging.ThreadLoggingMessages;
import io.mapsmessaging.utilities.threads.tasks.ConcurrentTaskScheduler;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextTaskScheduler extends ConcurrentTaskScheduler {


  private final Queue<FutureTask<?>> queue;

  public ContextTaskScheduler(@NonNull @NotNull String domain){
    super(domain);
    queue = new ConcurrentLinkedQueue<>();
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    if(shutdown || terminated){
      throw new RejectedExecutionException();
    }
    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());
    return addTask(new FutureTaskAccess<>(task));
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    if(shutdown || terminated){
      throw new RejectedExecutionException();
    }
    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());
    return addTask(new FutureTaskAccess<>(task, result));
  }

  protected <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task) {
    if(!shutdown) {
      queue.add(task);
      executeQueue();
    }
    else{
      task.cancel(true); // Mark it as cancelled
    }
    return task;
  }

  @Override
  public boolean isEmpty(){
    return queue.isEmpty();
  }

  @Override
  protected @Nullable FutureTask<?> poll(){
    return queue.poll();
  }


  @Getter
  private static final class FutureTaskAccess<T> extends FutureTask<T>{

    private final boolean isImmutable;

    public FutureTaskAccess(@NotNull Callable<T> callable) {
      super(callable);
      isImmutable = callable instanceof Immutable;
    }

    public FutureTaskAccess(@NotNull Runnable runnable, T result) {
      super(runnable, result);
      isImmutable = runnable instanceof Immutable;
    }
  }
}
