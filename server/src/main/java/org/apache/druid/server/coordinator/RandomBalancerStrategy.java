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

package org.apache.druid.server.coordinator;

import org.apache.druid.timeline.DataSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ThreadLocalRandom;

public class RandomBalancerStrategy implements BalancerStrategy
{
  @Override
  public ServerHolder findNewSegmentHomeReplicator(DataSegment proposalSegment, List<ServerHolder> serverHolders)
  {
    if (serverHolders.size() == 1) {
      return null;
    } else {
      ServerHolder holder = serverHolders.get(ThreadLocalRandom.current().nextInt(serverHolders.size()));
      while (holder.isServingSegment(proposalSegment)) {
        holder = serverHolders.get(ThreadLocalRandom.current().nextInt(serverHolders.size()));
      }
      return holder;
    }
  }

  @Override
  public ServerHolder findNewSegmentHomeBalancer(DataSegment proposalSegment, List<ServerHolder> serverHolders)
  {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public BalancerSegmentHolder pickSegmentToMove(List<ServerHolder> serverHolders)
  {
    return ReservoirSegmentSampler.getRandomBalancerSegmentHolder(serverHolders);
  }

  @Override
  public Iterator<ServerHolder> pickServersToDrop(DataSegment toDropSegment, NavigableSet<ServerHolder> serverHolders)
  {
    List<ServerHolder> serverList = new ArrayList<>(serverHolders);
    Collections.shuffle(serverList);
    return serverList.iterator();
  }

  @Override
  public void emitStats(String tier, CoordinatorStats stats, List<ServerHolder> serverHolderList)
  {
  }
}
