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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

public class Caller extends Thread {

  private static final int MAX_LOOPS = 100000;

  private final ResourceManager resourceManager;
  private final CountDownLatch countDownLatch;

  public Caller(ResourceManager resourceManager, CountDownLatch countDownLatch){
    this.resourceManager = resourceManager;
    this.countDownLatch = countDownLatch;
  }

  @Override
  public void run() {
    Random random = new Random(System.nanoTime());
    for(int x=0;x<MAX_LOOPS;x++){
      long val = Math.abs(random.nextInt());
      if(random.nextBoolean()){
        waitOnFuture(resourceManager.add(val));
      }
      else{
        waitOnFuture(resourceManager.sub(val));
      }
    }
    countDownLatch.countDown();
  }

  private void waitOnFuture(Future<Long> task){
    // We just wait here, it is up to your application to decide what it should do while it waits
    // for the task to complete
    while(!task.isDone() && !task.isCancelled()){
      LockSupport.parkNanos(1);
    }
  }
}
