/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.groupby.epinephelinae;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.MoreExecutors;
import io.druid.collections.ResourceHolder;
import io.druid.concurrent.Execs;
import io.druid.java.util.common.IAE;
import io.druid.java.util.common.parsers.CloseableIterator;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.CountAggregatorFactory;
import io.druid.query.groupby.epinephelinae.ConcurrentGrouperTest.TestResourceHolder;
import io.druid.query.groupby.epinephelinae.Grouper.Entry;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCombinerTest
{
  private static final int THREAD_NUM = 8;
  private static final ExecutorService SERVICE = Execs.multiThreaded(THREAD_NUM, "parallel-combiner-test-%d");
  private static final TestResourceHolder TEST_RESOURCE_HOLDER = new TestResourceHolder(512);

  private static final Supplier<ResourceHolder<ByteBuffer>> COMBINE_BUFFER_SUPPLIER =
      new Supplier<ResourceHolder<ByteBuffer>>()
      {
        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public ResourceHolder<ByteBuffer> get()
        {
          if (called.compareAndSet(false, true)) {
            return TEST_RESOURCE_HOLDER;
          } else {
            throw new IAE("should be called once");
          }
        }
      };

  private static final class TestIterator implements CloseableIterator<Entry<Long>>
  {
    private final Iterator<Entry<Long>> innerIterator;
    private boolean closed;

    TestIterator(Iterator<Entry<Long>> innerIterator)
    {
      this.innerIterator = innerIterator;
    }

    @Override
    public boolean hasNext()
    {
      return innerIterator.hasNext();
    }

    @Override
    public Entry<Long> next()
    {
      return innerIterator.next();
    }

    public boolean isClosed()
    {
      return closed;
    }

    @Override
    public void close() throws IOException
    {
      if (!closed) {
        closed = true;
      }
    }
  }

  @AfterClass
  public static void teardown()
  {
    SERVICE.shutdownNow();
  }

  @Test
  public void testCombine() throws IOException
  {
    final ParallelCombiner<Long> combiner = new ParallelCombiner<>(
        COMBINE_BUFFER_SUPPLIER,
        new AggregatorFactory[]{new CountAggregatorFactory("cnt").getCombiningFactory()},
        ConcurrentGrouperTest.KEY_SERDE_FACTORY,
        MoreExecutors.listeningDecorator(SERVICE),
        false,
        THREAD_NUM,
        0
    );

    final int numRows = 1000;
    final List<Entry<Long>> baseIterator = new ArrayList<>(numRows);
    for (long i = 0; i < numRows; i++) {
      baseIterator.add(new Entry<>(i, new Object[]{i * 10}));
    }

    final List<TestIterator> iterators = new ArrayList<>(8);
    for (int i = 0; i < 8; i++) {
      iterators.add(new TestIterator(baseIterator.iterator()));
    }

    try (final CloseableIterator<Entry<Long>> iterator = combiner.combine(iterators, new ArrayList<>())) {
      long expectedKey = 0;
      while (iterator.hasNext()) {
        Assert.assertEquals(new Entry<>(expectedKey, new Object[]{expectedKey++ * 80}), iterator.next());
      }
    }

    iterators.forEach(it -> Assert.assertTrue(it.isClosed()));
  }
}
