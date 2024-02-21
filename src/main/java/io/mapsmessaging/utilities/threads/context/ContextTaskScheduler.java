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
