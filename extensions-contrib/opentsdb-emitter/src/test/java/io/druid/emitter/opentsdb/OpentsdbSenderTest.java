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

package io.druid.emitter.opentsdb;

import org.junit.Assert;
import org.junit.Test;

public class OpentsdbSenderTest
{
  @Test
  public void testUrl() throws Exception
  {
    OpentsdbSender sender = new OpentsdbSender("localhost", 9999, 2000, 2000, 100);
    String expectedUrl = "http://localhost:9999/api/put";
    Assert.assertEquals(expectedUrl, sender.getWebResource().getURI().toString());
  }
}
