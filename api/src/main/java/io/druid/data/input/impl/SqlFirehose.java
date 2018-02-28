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
package io.druid.data.input.impl;

import com.google.common.collect.Iterators;
import io.druid.data.input.Firehose;
import io.druid.data.input.InputRow;
import io.druid.data.input.impl.prefetch.JsonIterator;
import io.druid.utils.Runnables;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class SqlFirehose implements Firehose
{
  private final Iterator<JsonIterator<Map<String, Object>>> resultIterator;
  private final InputRowParser parser;
  private final Closeable closer;
  private JsonIterator<Map<String, Object>> lineIterator = null;

  public SqlFirehose(
      Iterator lineIterators,
      InputRowParser<Map<String, Object>> parser,
      Closeable closer
  )
  {
    this.resultIterator = lineIterators;
    this.parser = parser;
    this.closer = closer;
  }

  @Override
  public boolean hasMore()
  {
    while ((lineIterator == null || !lineIterator.hasNext()) && resultIterator.hasNext()) {
      lineIterator = getNextLineIterator();
    }

    return lineIterator != null && lineIterator.hasNext();
  }

  @Nullable
  @Override
  public InputRow nextRow()
  {
    Map<String, Object> mapToParse = lineIterator.next();
    return (InputRow) Iterators.getOnlyElement(parser.parseBatch(mapToParse).iterator());
  }

  JsonIterator getNextLineIterator()
  {
    if (lineIterator != null) {
      lineIterator = null;
    }

    final JsonIterator iterator = resultIterator.next();
    return iterator;
  }

  @Override
  public Runnable commit()
  {
    return Runnables.getNoopRunnable();
  }

  @Override
  public void close() throws IOException
  {
    try {
      if (lineIterator != null) {
        lineIterator.close();
      }
    }
    catch (Throwable t) {
      try {
        if (closer != null) {
          closer.close();
        }
      }
      catch (Exception e) {
        t.addSuppressed(e);
      }
      throw t;
    }
    if (closer != null) {
      closer.close();
    }
  }
}
