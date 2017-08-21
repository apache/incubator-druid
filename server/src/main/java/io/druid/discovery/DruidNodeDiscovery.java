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

package io.druid.discovery;

import java.util.Collection;
import java.util.List;

/**
 * Interface for discovering Druid Nodes announced by DruidNodeAnnouncer.
 */
public interface DruidNodeDiscovery
{
  Collection<DiscoveryDruidNode> getAllNodes();
  void registerListener(Listener listener);

  interface Listener
  {
    // Implementation must ensure that methods below are never called in parallel and Listener implementation
    // is not expected to be threadsafe.

    void nodesAdded(List<DiscoveryDruidNode> nodes);
    void nodesRemoved(List<DiscoveryDruidNode> nodes);
  }
}
