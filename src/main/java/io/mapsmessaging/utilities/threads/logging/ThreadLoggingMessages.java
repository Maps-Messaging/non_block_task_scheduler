package io.mapsmessaging.utilities.threads.logging;

import io.mapsmessaging.logging.Category;
import io.mapsmessaging.logging.LEVEL;
import io.mapsmessaging.logging.LogMessage;
import lombok.Getter;

public enum ThreadLoggingMessages implements LogMessage {

  //region Priority Scheduler
  PRIORITY_CREATION(LEVEL.TRACE, THREAD_CATEGORY.PRIORITY, "Constructed new priority task scheduler for domain{} with {} priority levels" ),
  PRIORITY_SUBMIT(LEVEL.TRACE, THREAD_CATEGORY.PRIORITY, "Submitting task {} at priority {}" ),
  //endregion

  //region Scheduler
  SCHEDULER_SUBMIT_TASK(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Scheduling task {}" ),
  SCHEDULER_EXECUTING_TASK(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Executing task {}" ),
  SCHEDULER_IS_IDLE(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Task queue is idle" ),

  SCHEDULER_SHUTTING_DOWN(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Scheduler is being shut down" ),
  SCHEDULER_THREAD_OFF_LOADING(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Scheduler starting off-loading thread to continue tasks" ),
  SCHEDULER_SHUTDOWN(LEVEL.TRACE, THREAD_CATEGORY.CONCURRENT, "Scheduler has been closed, unable to submit new task, {}" ),
  //endregion


  ;


  private final @Getter String message;
  private final @Getter LEVEL level;
  private final @Getter Category category;
  private final @Getter int parameterCount;

  ThreadLoggingMessages(LEVEL level, THREAD_CATEGORY category, String message) {
    this.message = message;
    this.level = level;
    this.category = category;
    int location = message.indexOf("{}");
    int count = 0;
    while (location != -1) {
      count++;
      location = message.indexOf("{}", location + 2);
    }
    this.parameterCount = count;
  }

  public enum THREAD_CATEGORY implements Category {
    CONCURRENT("Concurrent"),
    PRIORITY("Priority");

    private final @Getter String description;

    public String getDivision(){
      return "Scheduler";
    }

    THREAD_CATEGORY(String description) {
      this.description = description;
    }
  }
}
