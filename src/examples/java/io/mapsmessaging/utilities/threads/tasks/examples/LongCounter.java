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

import java.util.concurrent.atomic.AtomicLong;

public class LongCounter {

  private long counter;

  private final AtomicLong validationLong;

  public LongCounter(){
    counter = 0;
    validationLong = new AtomicLong(0);
  }

  public long add(long addition){
    counter += addition;
    validationLong.addAndGet(addition);
    return counter;
  }

  public long sub(long subtraction){
    counter -= subtraction;
    validationLong.addAndGet(subtraction*-1);
    return counter;
  }

  public long getCounter(){
    return counter;
  }

  public long getValidation(){
    return validationLong.get();
  }
}
