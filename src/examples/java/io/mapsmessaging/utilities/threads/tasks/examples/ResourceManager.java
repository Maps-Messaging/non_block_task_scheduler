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
package io.mapsmessaging.utilities.threads.tasks.examples;

import io.mapsmessaging.utilities.threads.tasks.ConcurrentTaskScheduler;
import io.mapsmessaging.utilities.threads.tasks.SingleConcurrentTaskScheduler;
import io.mapsmessaging.utilities.threads.tasks.examples.tasks.AdderTask;
import io.mapsmessaging.utilities.threads.tasks.examples.tasks.SubTask;
import java.util.concurrent.FutureTask;

public class ResourceManager {

  private final ConcurrentTaskScheduler<Long> concurrentTaskScheduler;
  private final LongCounter counter;


  public ResourceManager(){
    concurrentTaskScheduler = new SingleConcurrentTaskScheduler<>("UniqueDomainName");
    counter = new LongCounter();
  }

  public FutureTask<Long> add(long value){
    AdderTask adderTask = new AdderTask(value, counter);
    FutureTask<Long> future = new FutureTask<>(adderTask);
    concurrentTaskScheduler.addTask(future);
    return future;
  }

  public FutureTask<Long> sub(long value){
    SubTask subTask = new SubTask(value, counter);
    FutureTask<Long> future = new FutureTask<>(subTask);
    concurrentTaskScheduler.addTask(future);
    return future;
  }

  public long getResourceLong(){
    return counter.getCounter();
  }

  public long getValidationLong(){
    return counter.getValidation();
  }

  public String toString(){
    return concurrentTaskScheduler.toString();
  }
}
