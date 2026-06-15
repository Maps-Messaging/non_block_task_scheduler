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

import io.mapsmessaging.logging.Logger;
import io.mapsmessaging.logging.LoggerFactory;
import io.mapsmessaging.logging.ThreadContext;
import io.mapsmessaging.utilities.threads.logging.ThreadLoggingMessages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class abstraction is a thread safe task scheduler that will manage the threading access of the task schedulers.
 * <br>
 * It has no threads itself, rather, when a task is added to schedule, if it is the first task to be queued then this thread is used
 * to execute the task and any additional tasks that have been queued while the first task was running. If this thread runs more than
 * a configured number of tasks then it will off-load future tasks to a dedicated thread and unwind itself.
 * <br>
 * This reduces the overall number of threads that are just waiting on queues and reduces the time for a task to be executed. The important
 * aspect of this mechanism is that no locks are required to add or execute tasks and can be used to remove the standard
 * <code>
 *   synchronized(lock){
 *      // doSomething
 *   }
 * </code>
 *
 * This can change by using Task that can be queued. Since the queue is executed by a single thread, no locking is required and order is guaranteed.
 *
 * The <code>domain</code> field is used by tasks to ensure that the executing thread is meant to be running the code, offering the ability to ensure no code
 * by passes the task queue mechanism.
 *
 * @since 1.0
 * @author Matthew Buckton
 * @version 2.0
 */
@ToString
public abstract class ConcurrentTaskScheduler implements TaskScheduler {

  private static final int POOL_DEPTH;

  static {
    int processorCount = Runtime.getRuntime().availableProcessors();
    String configuredValue = System.getProperty("PoolDepth", "" + processorCount);
    try {
      processorCount = Integer.parseInt(configuredValue);
    } catch (NumberFormatException exception) {
      // Ignore here
    }
    POOL_DEPTH = processorCount;
  }

  private static final ExecutorService executorOffloadService = Executors.newWorkStealingPool(POOL_DEPTH);

  protected static final int MAX_TASK_EXECUTION_EXTERNAL_THREAD = 10;
  protected static final int MAX_TASK_EXECUTION_SCHEDULED_THREAD = Integer.MAX_VALUE;

  private static final String DOMAIN = "domain";

  private final ThreadStateContext context;

  protected final Logger logger;
  protected final AtomicLong outstanding;
  protected final AtomicLong maxOutstanding;
  protected final LongAdder offloadedCount;
  protected final LongAdder totalQueued;
  protected final Runnable offloadThread;

  protected volatile boolean shutdown;
  protected volatile boolean terminated;

  protected ConcurrentTaskScheduler(@NonNull @NotNull String domain) {
    logger = LoggerFactory.getLogger(getClass());

    context = new ThreadStateContext();
    context.add(DOMAIN, domain);
    context.add("TaskQueue", this);

    outstanding = new AtomicLong(0);
    maxOutstanding = new AtomicLong(0);
    totalQueued = new LongAdder();
    offloadedCount = new LongAdder();
    offloadThread = new QueueRunner();

    shutdown = false;
    terminated = false;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return terminated;
  }

  @Override
  public void shutdown() {
    shutdown = true;
    logger.log(ThreadLoggingMessages.SCHEDULER_SHUTTING_DOWN);
    signalTerminatedIfComplete();
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown = true;

    List<Runnable> activeTasks = new ArrayList<>();
    FutureTask<?> task = poll();

    while (task != null) {
      task.cancel(true);
      activeTasks.add(task);
      decrementOutstanding();
      task = poll();
    }

    signalTerminatedIfComplete();
    return activeTasks;
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }

    long remainingNanos = unit.toNanos(timeout);
    if (isTerminated()) {
      return true;
    }
    if (remainingNanos <= 0L) {
      return false;
    }

    long deadline = System.nanoTime() + remainingNanos;

    synchronized (this) {
      while (!isTerminated()) {
        if (remainingNanos <= 0L) {
          return false;
        }

        long waitMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        wait(waitMillis > 0L ? waitMillis : 1L);
        remainingNanos = deadline - System.nanoTime();
      }
      return true;
    }
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    rejectIfShutdown();

    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());

    return addTask(new FutureTask<>(task));
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    rejectIfShutdown();

    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());

    return addTask(new FutureTask<>(task, result));
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable task) {
    rejectIfShutdown();

    logger.log(ThreadLoggingMessages.SCHEDULER_SUBMIT_TASK, task.getClass());

    return addTask(new FutureTask<>(task, new Object()));
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    rejectIfShutdown();

    List<FutureTask<T>> futureTasks = createFutureTasks(tasks);

    if (isSchedulerThread()) {
      return invokeAllInline(futureTasks);
    }

    boolean completed = false;
    try {
      for (FutureTask<T> futureTask : futureTasks) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        addTask(futureTask);
      }

      waitForAll(futureTasks);
      completed = true;
      return asFutureList(futureTasks);
    } finally {
      if (!completed) {
        cancelIncomplete(futureTasks);
      }
    }
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(
      @NotNull Collection<? extends Callable<T>> tasks,
      long timeout,
      @NotNull TimeUnit unit
  ) throws InterruptedException {
    rejectIfShutdown();

    long timeoutNanos = unit.toNanos(timeout);
    long deadline = System.nanoTime() + timeoutNanos;
    List<FutureTask<T>> futureTasks = createFutureTasks(tasks);

    if (timeoutNanos <= 0L) {
      cancelIncomplete(futureTasks);
      return asFutureList(futureTasks);
    }

    if (isSchedulerThread()) {
      return invokeAllInline(futureTasks, deadline);
    }

    boolean completed = false;
    try {
      for (FutureTask<T> futureTask : futureTasks) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        if (deadline - System.nanoTime() <= 0L) {
          cancelIncomplete(futureTasks);
          completed = true;
          return asFutureList(futureTasks);
        }

        addTask(futureTask);
      }

      for (FutureTask<T> futureTask : futureTasks) {
        if (!futureTask.isDone()) {
          long remainingNanos = deadline - System.nanoTime();
          if (remainingNanos <= 0L) {
            cancelIncomplete(futureTasks);
            completed = true;
            return asFutureList(futureTasks);
          }

          try {
            futureTask.get(remainingNanos, TimeUnit.NANOSECONDS);
          } catch (CancellationException | ExecutionException exception) {
            // invokeAll returns futures; failures are observed through Future.get().
          } catch (TimeoutException exception) {
            cancelIncomplete(futureTasks);
            completed = true;
            return asFutureList(futureTasks);
          }
        }
      }

      completed = true;
      return asFutureList(futureTasks);
    } finally {
      if (!completed) {
        cancelIncomplete(futureTasks);
      }
    }
  }

  @NotNull
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    rejectIfShutdown();

    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("Task collection must not be empty");
    }

    if (isSchedulerThread()) {
      return invokeAnyInline(tasks);
    }

    List<Future<T>> futures = new ArrayList<>();
    ExecutionException lastFailure = null;

    try {
      for (Callable<T> callable : tasks) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        futures.add(futureTask);
        addTask(futureTask);

        try {
          return futureTask.get();
        } catch (CancellationException exception) {
          lastFailure = new ExecutionException(exception);
        } catch (ExecutionException exception) {
          lastFailure = exception;
        }
      }
    } finally {
      cancelIncomplete(futures);
    }

    if (lastFailure != null) {
      throw lastFailure;
    }
    throw new ExecutionException("No task completed successfully", null);
  }

  @Override
  public <T> T invokeAny(
      @NotNull Collection<? extends Callable<T>> tasks,
      long timeout,
      @NotNull TimeUnit unit
  ) throws InterruptedException, ExecutionException, TimeoutException {
    rejectIfShutdown();

    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("Task collection must not be empty");
    }

    long timeoutNanos = unit.toNanos(timeout);
    long deadline = System.nanoTime() + timeoutNanos;

    if (timeoutNanos <= 0L) {
      throw new TimeoutException();
    }

    if (isSchedulerThread()) {
      return invokeAnyInline(tasks, deadline);
    }

    List<Future<T>> futures = new ArrayList<>();
    ExecutionException lastFailure = null;

    try {
      for (Callable<T> callable : tasks) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
          throw new TimeoutException();
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        futures.add(futureTask);
        addTask(futureTask);

        remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
          throw new TimeoutException();
        }

        try {
          return futureTask.isDone()
              ? futureTask.get()
              : futureTask.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (CancellationException exception) {
          lastFailure = new ExecutionException(exception);
        } catch (ExecutionException exception) {
          lastFailure = exception;
        }
      }
    } finally {
      cancelIncomplete(futures);
    }

    if (lastFailure != null) {
      throw lastFailure;
    }
    throw new ExecutionException("No task completed successfully", null);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    submit(command);
  }

  public long getOffloadCount() {
    return offloadedCount.sum();
  }

  public long getMaxOutstanding() {
    return maxOutstanding.get();
  }

  public long getTotalTasksQueued() {
    return totalQueued.sum();
  }

  public long getOutstanding() {
    return outstanding.get();
  }

  @SuppressWarnings("java:S1452")
  protected abstract @Nullable FutureTask<?> poll();

  protected abstract <T> FutureTask<T> addTask(@NonNull @NotNull FutureTask<T> task);

  protected void internalExecuteQueue(int maxTaskExecutions) {
    Map<String, String> logContext = ThreadContext.getContext();
    ThreadStateContext originalDomain = ThreadLocalContext.get();

    ThreadLocalContext.set(context);

    try {
      taskRun(maxTaskExecutions);
    } finally {
      ThreadContext.clearMap();
      if (logContext != null) {
        ThreadContext.putAll(logContext);
      }

      if (originalDomain != null) {
        ThreadLocalContext.set(originalDomain);
      } else {
        ThreadLocalContext.remove();
      }
    }
  }

  protected void rejectIfShutdown() {
    if (shutdown || terminated) {
      throw new RejectedExecutionException();
    }
  }

  protected boolean reserveTaskSlot() {
    rejectIfShutdown();

    totalQueued.increment();

    long count = outstanding.incrementAndGet();
    maxOutstanding.accumulateAndGet(count, Math::max);

    return count == 1;
  }

  protected void executeReservedTaskSlot(boolean runnerRequired) {
    if (runnerRequired) {
      internalExecuteQueue(MAX_TASK_EXECUTION_EXTERNAL_THREAD);
    }
  }

  protected void releaseReservedTaskSlot() {
    decrementOutstanding();
    signalTerminatedIfComplete();
  }

  private void taskRun(int maxTaskExecutions) {
    int runnerCount = 0;

    while (true) {
      Runnable task = poll();

      if (task == null) {
        if (outstanding.get() == 0) {
          logger.log(ThreadLoggingMessages.SCHEDULER_IS_IDLE);
          signalTerminatedIfComplete();
          return;
        }

        LockSupport.parkNanos(1000L);
        continue;
      }

      logger.log(ThreadLoggingMessages.SCHEDULER_EXECUTING_TASK, task.getClass());

      task.run();

      Thread.interrupted();
      runnerCount++;

      long count = decrementOutstanding();

      if (count == 0) {
        logger.log(ThreadLoggingMessages.SCHEDULER_IS_IDLE);
        signalTerminatedIfComplete();
        return;
      }

      if (runnerCount >= maxTaskExecutions) {
        logger.log(ThreadLoggingMessages.SCHEDULER_THREAD_OFF_LOADING);
        offloadedCount.increment();
        executorOffloadService.submit(offloadThread);
        return;
      }
    }
  }

  private long decrementOutstanding() {
    long count = outstanding.decrementAndGet();
    if (count < 0) {
      outstanding.set(0);
      return 0;
    }
    return count;
  }

  private void signalTerminatedIfComplete() {
    if (shutdown && outstanding.get() == 0 && isEmpty()) {
      synchronized (this) {
        if (!terminated && outstanding.get() == 0 && isEmpty()) {
          terminated = true;
          notifyAll();
        }
      }
    }
  }

  private boolean isSchedulerThread() {
    ThreadStateContext threadStateContext = ThreadLocalContext.get();
    if (threadStateContext == null) {
      return false;
    }

    Object localDomain = context.get(DOMAIN);
    Object threadDomain = threadStateContext.get(DOMAIN);

    return localDomain != null
        && threadDomain != null
        && localDomain.toString().equalsIgnoreCase(threadDomain.toString());
  }

  private <T> List<FutureTask<T>> createFutureTasks(Collection<? extends Callable<T>> tasks) {
    List<FutureTask<T>> futureTasks = new ArrayList<>();

    for (Callable<T> callable : tasks) {
      futureTasks.add(new FutureTask<>(callable));
    }

    return futureTasks;
  }

  private <T> List<Future<T>> asFutureList(List<FutureTask<T>> futureTasks) {
    return new ArrayList<>(futureTasks);
  }

  private <T> List<Future<T>> invokeAllInline(List<FutureTask<T>> futureTasks) throws InterruptedException {
    boolean completed = false;

    try {
      for (FutureTask<T> futureTask : futureTasks) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        futureTask.run();
      }

      completed = true;
      return asFutureList(futureTasks);
    } finally {
      if (!completed) {
        cancelIncomplete(futureTasks);
      }
    }
  }

  private <T> List<Future<T>> invokeAllInline(List<FutureTask<T>> futureTasks, long deadline)
      throws InterruptedException {
    for (FutureTask<T> futureTask : futureTasks) {
      if (Thread.currentThread().isInterrupted()) {
        cancelIncomplete(futureTasks);
        throw new InterruptedException();
      }

      if (deadline - System.nanoTime() <= 0L) {
        cancelIncomplete(futureTasks);
        return asFutureList(futureTasks);
      }

      futureTask.run();
    }

    if (deadline - System.nanoTime() <= 0L) {
      cancelIncomplete(futureTasks);
    }

    return asFutureList(futureTasks);
  }

  private <T> T invokeAnyInline(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    ExecutionException lastFailure = null;

    for (Callable<T> callable : tasks) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }

      FutureTask<T> futureTask = new FutureTask<>(callable);
      futureTask.run();

      try {
        return futureTask.get();
      } catch (CancellationException exception) {
        lastFailure = new ExecutionException(exception);
      } catch (ExecutionException exception) {
        lastFailure = exception;
      }
    }

    if (lastFailure != null) {
      throw lastFailure;
    }

    throw new ExecutionException("No task completed successfully", null);
  }

  private <T> T invokeAnyInline(Collection<? extends Callable<T>> tasks, long deadline)
      throws InterruptedException, ExecutionException, TimeoutException {
    ExecutionException lastFailure = null;

    for (Callable<T> callable : tasks) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }

      if (deadline - System.nanoTime() <= 0L) {
        throw new TimeoutException();
      }

      FutureTask<T> futureTask = new FutureTask<>(callable);
      futureTask.run();

      if (deadline - System.nanoTime() <= 0L) {
        throw new TimeoutException();
      }

      try {
        return futureTask.get();
      } catch (CancellationException exception) {
        lastFailure = new ExecutionException(exception);
      } catch (ExecutionException exception) {
        lastFailure = exception;
      }
    }

    if (lastFailure != null) {
      throw lastFailure;
    }

    throw new ExecutionException("No task completed successfully", null);
  }

  private <T> void waitForAll(List<FutureTask<T>> futureTasks) throws InterruptedException {
    for (FutureTask<T> futureTask : futureTasks) {
      if (!futureTask.isDone()) {
        try {
          futureTask.get();
        } catch (CancellationException | ExecutionException exception) {
          // invokeAll returns futures; failures are observed through Future.get().
        }
      }
    }
  }

  private void cancelIncomplete(Collection<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      if (!future.isDone()) {
        future.cancel(true);
      }
    }
  }

  private class QueueRunner implements Runnable {

    private final Map<String, String> context;

    QueueRunner() {
      context = ThreadContext.getContext();
    }

    @Override
    public void run() {
      String threadName = Thread.currentThread().getName();
      Thread.currentThread().setName("TaskQueue_OffLoad");

      ThreadContext.clearMap();
      if (context != null) {
        ThreadContext.putAll(context);
      }

      try {
        internalExecuteQueue(MAX_TASK_EXECUTION_SCHEDULED_THREAD);
      } finally {
        Thread.currentThread().setName(threadName);
      }
    }
  }
}