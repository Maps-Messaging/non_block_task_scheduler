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


import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * This class, is used by the Task Queue to validate the domain that the thread is running is is authorised to
 * be running the tasks. It is a useful mechanism to ensure only the correct task queue is running the correct task
 *
 *  @since 1.0
 *  @author Matthew Buckton
 *  @version 1.0
 */

@SuppressWarnings("java:S6548") // yes it is a singleton
@ToString
public class ThreadLocalContext {

  private static final boolean DEBUG_DOMAIN;
  static{
    String value = System.getProperty("debug_domain", "false");
    boolean check = false;
    try {
      check = Boolean.parseBoolean(value);
    } catch (Exception exception) {
      // ignore we simply disable it
    }
    DEBUG_DOMAIN = check;
  }

  private static class Holder {
    static final ThreadLocalContext INSTANCE = new ThreadLocalContext();
  }
  public static ThreadLocalContext getInstance() {
    return ThreadLocalContext.Holder.INSTANCE;
  }


  protected final ThreadLocal<ThreadStateContext> context;

  public static ThreadStateContext get(){
    return getInstance().context.get();
  }

  public static void set(@NonNull @NotNull ThreadStateContext entry){
    getInstance().context.set(entry);
  }

  public static void remove(){
    getInstance().context.remove();
  }

  public static void checkDomain(@NonNull @NotNull String domain){
    if(DEBUG_DOMAIN) {
      var response = false;
      ThreadStateContext context = ThreadLocalContext.get();
      Object check = "";
      if (context != null) {
        check = context.get("domain");
        if (check instanceof String) {
          response = domain.equals(check);
        }
      }
      if (!response) {
        throw new DomainDebuggingException("Incorrect thread domain detected! > " + check + " Expected " + domain);
      }
    }
  }

  private ThreadLocalContext(){
    context = new ThreadLocal<>();
  }

  private static class DomainDebuggingException extends RuntimeException{

    public DomainDebuggingException(String msg) {
      super(msg);
    }
  }
}
