/*
 * Druid - a distributed column store.
 * Copyright 2015 - Yahoo! Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class StupidResourceHolderTest
{
  private StupidResourceHolder<String> resourceHolder;

  @Test
  public void testCreateAndGet() throws IOException
  {
    String expected = "String";
    resourceHolder = StupidResourceHolder.create(expected);
    String actual = resourceHolder.get();
    Assert.assertEquals(expected,actual);
    resourceHolder.close();
  }
}