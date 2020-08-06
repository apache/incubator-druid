/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.utils;

import com.google.common.base.Throwables;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.matchers.ThrowableMessageMatcher;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class CloseableUtilsTest
{
  private final TestCloseable quietCloseable = new TestCloseable(null);
  private final TestCloseable quietCloseable2 = new TestCloseable(null);
  private final TestCloseable ioExceptionCloseable = new TestCloseable(new IOException());
  private final TestCloseable runtimeExceptionCloseable = new TestCloseable(new IllegalArgumentException());

  // For closeAndSuppressException tests.
  private final AtomicLong chomped = new AtomicLong();
  private final Consumer<Exception> chomper = e -> chomped.incrementAndGet();

  @Test
  public void test_closeAll_quiet() throws IOException
  {
    CloseableUtils.closeAll(quietCloseable, quietCloseable2);
    assertClosed(quietCloseable, quietCloseable2);
  }

  @Test
  public void test_closeAll_loud()
  {
    Exception e = null;
    try {
      CloseableUtils.closeAll(quietCloseable, ioExceptionCloseable, quietCloseable2, runtimeExceptionCloseable);
    }
    catch (Exception e2) {
      e = e2;
    }

    assertClosed(quietCloseable, ioExceptionCloseable, quietCloseable2, runtimeExceptionCloseable);

    // First exception
    Assert.assertThat(e, CoreMatchers.instanceOf(IOException.class));

    // Second exception
    Assert.assertEquals(1, e.getSuppressed().length);
    Assert.assertThat(e.getSuppressed()[0], CoreMatchers.instanceOf(RuntimeException.class));
  }

  @Test
  public void test_closeAndWrapExceptions_null()
  {
    CloseableUtils.closeAndWrapExceptions(null);
    // Nothing happens.
  }

  @Test
  public void test_closeAndWrapExceptions_quiet()
  {
    CloseableUtils.closeAndWrapExceptions(quietCloseable);
    assertClosed(quietCloseable);
  }

  @Test
  public void test_closeAndWrapExceptions_ioException()
  {
    Exception e = null;
    try {
      CloseableUtils.closeAndWrapExceptions(ioExceptionCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    assertClosed(ioExceptionCloseable);
    Assert.assertThat(e, CoreMatchers.instanceOf(RuntimeException.class));
  }

  @Test
  public void test_closeAndWrapExceptions_runtimeException()
  {
    Exception e = null;
    try {
      CloseableUtils.closeAndWrapExceptions(runtimeExceptionCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    assertClosed(runtimeExceptionCloseable);
    Assert.assertThat(e, CoreMatchers.instanceOf(IllegalArgumentException.class));
  }

  @Test
  public void test_closeAndSuppressExceptions_null()
  {
    CloseableUtils.closeAndSuppressExceptions(null, chomper);
    Assert.assertEquals(0, chomped.get());
  }

  @Test
  public void test_closeAndSuppressExceptions_quiet()
  {
    CloseableUtils.closeAndSuppressExceptions(quietCloseable, chomper);
    assertClosed(quietCloseable);
    Assert.assertEquals(0, chomped.get());
  }

  @Test
  public void test_closeAndSuppressExceptions_ioException()
  {
    CloseableUtils.closeAndSuppressExceptions(ioExceptionCloseable, chomper);
    assertClosed(ioExceptionCloseable);
    Assert.assertEquals(1, chomped.get());
  }

  @Test
  public void test_closeAndSuppressExceptions_runtimeException()
  {
    CloseableUtils.closeAndSuppressExceptions(runtimeExceptionCloseable, chomper);
    assertClosed(runtimeExceptionCloseable);
    Assert.assertEquals(1, chomped.get());
  }

  @Test
  public void test_closeInCatch_improper() throws Exception
  {
    Exception e = null;
    try {
      //noinspection ThrowableNotThrown
      CloseableUtils.closeInCatch(null, quietCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    Assert.assertTrue(quietCloseable.isClosed());

    Assert.assertThat(e, CoreMatchers.instanceOf(IllegalStateException.class));
    Assert.assertThat(
        e,
        ThrowableMessageMatcher.hasMessage(CoreMatchers.startsWith("Must be called with non-null caught exception"))
    );
  }

  @Test
  public void test_closeInCatch_quiet() throws Exception
  {
    Exception e = null;
    try {
      //noinspection ThrowableNotThrown
      CloseableUtils.closeInCatch(new RuntimeException("this one was caught"), quietCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    Assert.assertTrue(quietCloseable.isClosed());

    Assert.assertThat(e, CoreMatchers.instanceOf(RuntimeException.class));
    Assert.assertThat(
        e,
        ThrowableMessageMatcher.hasMessage(CoreMatchers.startsWith("this one was caught"))
    );
  }

  @Test
  public void test_closeInCatch_ioException()
  {
    Exception e = null;
    try {
      //noinspection ThrowableNotThrown
      CloseableUtils.closeInCatch(new RuntimeException("this one was caught"), ioExceptionCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    Assert.assertTrue(ioExceptionCloseable.isClosed());

    // First exception
    Assert.assertThat(e, CoreMatchers.instanceOf(RuntimeException.class));
    Assert.assertThat(
        e,
        ThrowableMessageMatcher.hasMessage(CoreMatchers.startsWith("this one was caught"))
    );

    // Second exception
    Assert.assertEquals(1, e.getSuppressed().length);
    Assert.assertThat(e.getSuppressed()[0], CoreMatchers.instanceOf(IOException.class));
  }

  @Test
  public void test_closeInCatch_runtimeException()
  {
    Exception e = null;
    try {
      //noinspection ThrowableNotThrown
      CloseableUtils.closeInCatch(new RuntimeException("this one was caught"), runtimeExceptionCloseable);
    }
    catch (Exception e1) {
      e = e1;
    }

    Assert.assertTrue(runtimeExceptionCloseable.isClosed());

    // First exception
    Assert.assertThat(e, CoreMatchers.instanceOf(RuntimeException.class));
    Assert.assertThat(
        e,
        ThrowableMessageMatcher.hasMessage(CoreMatchers.startsWith("this one was caught"))
    );

    // Second exception
    Assert.assertEquals(1, e.getSuppressed().length);
    Assert.assertThat(e.getSuppressed()[0], CoreMatchers.instanceOf(IllegalArgumentException.class));
  }

  private static void assertClosed(final TestCloseable... closeables)
  {
    for (TestCloseable closeable : closeables) {
      Assert.assertTrue(closeable.isClosed());
    }
  }

  private static class TestCloseable implements Closeable
  {
    @Nullable
    private final Exception e;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TestCloseable(@Nullable Exception e)
    {
      this.e = e;
    }

    @Override
    public void close() throws IOException
    {
      closed.set(true);
      if (e != null) {
        Throwables.propagateIfInstanceOf(e, IOException.class);
        throw Throwables.propagate(e);
      }
    }

    public boolean isClosed()
    {
      return closed.get();
    }
  }
}
