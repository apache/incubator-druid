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

package org.apache.druid.testing.utils;

import java.util.Map;

public interface StreamAdminClient
{
  void createStream(String streamName, int partitionCount, Map<String, String> tags) throws Exception;

  void deleteStream(String streamName) throws Exception;

  void updateShardCount(String streamName, int newPartitionCount, boolean blocksUntilStarted) throws Exception;

  boolean isStreamActive(String streamName) throws Exception;

  int getStreamShardCount(String streamName) throws Exception;

  boolean verfiyShardCountUpdated(String streamName, int oldShardCount, int newShardCount) throws Exception;
}
