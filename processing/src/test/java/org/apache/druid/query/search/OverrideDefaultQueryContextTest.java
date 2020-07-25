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

package org.apache.druid.query.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.druid.java.util.common.Numbers;
import org.apache.druid.query.OverrideDefaultQueryContext;
import org.apache.druid.query.QueryContexts.Vectorize;
import org.apache.druid.segment.TestHelper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class OverrideDefaultQueryContextTest
{
  @Test
  public void testSerde() throws IOException
  {
    final ObjectMapper mapper = TestHelper.makeJsonMapper();
    final String json = "{"
                        + "\"vectorize\" : \"force\","
                        + "\"vectorSize\" : 1"
                        + "}";
    final OverrideDefaultQueryContext config = mapper.readValue(json, OverrideDefaultQueryContext.class);
    Assert.assertEquals(Vectorize.FORCE, Vectorize.fromString(config.getConfigs().get("vectorize").toString()));
    Assert.assertEquals(1, Numbers.parseInt(config.getConfigs().get("vectorSize")));
  }

  @Test
  public void testDefault()
  {
    final OverrideDefaultQueryContext config = new OverrideDefaultQueryContext();
    Assert.assertNotNull(config.getConfigs());
    Assert.assertEquals(0, config.getConfigs().size());
  }

  @Test
  public void testSetAndGet()
  {
    final OverrideDefaultQueryContext config = new OverrideDefaultQueryContext();
    String key = "key1";
    String value = "value1";
    config.setConfigs(ImmutableMap.of(key, value));
    Assert.assertNotNull(config.getConfigs());
    Assert.assertEquals(1, config.getConfigs().size());
    Assert.assertTrue(config.getConfigs().containsKey(key));
    Assert.assertEquals(value, config.getConfigs().get(key));
  }
}