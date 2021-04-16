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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DemoRunner {

  private static final int MAX_CALLERS = 100;

  public static void main(String[] args) throws InterruptedException {
    // Create the resource
    ResourceManager resource = new ResourceManager();
    CountDownLatch countDownLatch = new CountDownLatch(MAX_CALLERS);

    // Create the callers
    List<Caller> workerList = new ArrayList<>();
    for(int x=0;x<MAX_CALLERS;x++){
      workerList.add(new Caller(resource, countDownLatch));
    }

    // Now start the callers and wait for completion
    for(Caller caller:workerList){
      caller.start();
    }

    if(!countDownLatch.await(300, TimeUnit.SECONDS)){
      System.err.println("Tasks still running, you may need to tune the numbers to match your machine");
    }
    else{
      System.err.println(resource.getResourceLong() + " == " + resource.getValidationLong() + " Diff:" + (resource.getResourceLong() - resource.getValidationLong()));
    }
    System.exit(1);
  }
}
