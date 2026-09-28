/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *
 */

package io.mapsmessaging.utilities.threads.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThreadContextTest {

  @AfterEach
  void clearThreadLocalContext() {
    ThreadLocalContext.remove();
  }

  @Test
  void stateContextStoresValues() {
    ThreadStateContext context = new ThreadStateContext();

    assertNull(context.get("missing"));
    context.add("name", "value");

    assertEquals("value", context.get("name"));
  }

  @Test
  void threadLocalContextStoresAndRemovesState() {
    ThreadStateContext context = new ThreadStateContext();

    assertNull(ThreadLocalContext.get());
    ThreadLocalContext.set(context);
    assertSame(context, ThreadLocalContext.get());

    ThreadLocalContext.remove();
    assertNull(ThreadLocalContext.get());
  }

  @Test
  void singletonIsStable() {
    assertSame(ThreadLocalContext.getInstance(), ThreadLocalContext.getInstance());
  }
}
