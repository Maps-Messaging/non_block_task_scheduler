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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


  @Test
  void domainValidationAcceptsMatchingDomain() {
    ThreadStateContext context = new ThreadStateContext();
    context.add("domain", "test");
    ThreadLocalContext.set(context);

    ThreadLocalContext.validateDomain("test");
  }

  @Test
  void domainValidationRejectsMissingContext() {
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> ThreadLocalContext.validateDomain("test")
    );

    assertTrue(exception.getMessage().contains("Expected test"));
  }

  @Test
  void domainValidationRejectsWrongDomain() {
    ThreadStateContext context = new ThreadStateContext();
    context.add("domain", "other");
    ThreadLocalContext.set(context);

    assertThrows(RuntimeException.class, () -> ThreadLocalContext.validateDomain("test"));
  }

  @Test
  void domainValidationRejectsNonStringDomain() {
    ThreadStateContext context = new ThreadStateContext();
    context.add("domain", 42);
    ThreadLocalContext.set(context);

    assertThrows(RuntimeException.class, () -> ThreadLocalContext.validateDomain("test"));
  }
}
