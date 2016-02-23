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

package io.druid.curator.discovery;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.net.HostAndPort;
import com.metamx.common.logger.Logger;
import io.druid.client.DruidServerDiscovery;
import io.druid.client.selector.DiscoverySelector;
import io.druid.client.selector.Server;
import io.druid.server.coordination.DruidServerMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class ServerDiscoverySelector implements DiscoverySelector<Server>
{
  private static final Logger log = new Logger(ServerDiscoverySelector.class);
  private final DruidServerDiscovery druidServerDiscovery;
  private final Function<DruidServerDiscovery, List<DruidServerMetadata>> selectFunction;
  private final AtomicInteger roundRobinIdx;

  public ServerDiscoverySelector(
      DruidServerDiscovery druidServerDiscovery,
      Function<DruidServerDiscovery, List<DruidServerMetadata>> selectFunction
  )
  {
    this.roundRobinIdx = new AtomicInteger(0);
    this.druidServerDiscovery = druidServerDiscovery;
    this.selectFunction = selectFunction == null ? new NoopSelectFunction() : selectFunction;
  }

  private static final Function<DruidServerMetadata, Server> TO_SERVER = new Function<DruidServerMetadata, Server>()
  {
    @Override
    public Server apply(final DruidServerMetadata instance)
    {
      return new Server()
      {
        @Override
        public String getHost()
        {
          return HostAndPort.fromParts(instance.getHostText(), instance.getPort()).toString();
        }

        @Override
        public String getAddress()
        {
          return instance.getHostText();
        }

        @Override
        public int getPort()
        {
          return instance.getPort();
        }

        @Override
        public String getScheme()
        {
          return "http";
        }
      };
    }
  };

  @Override
  public Server pick()
  {
    final DruidServerMetadata instance;
    try {
      final List<DruidServerMetadata> candidates = selectFunction.apply(druidServerDiscovery);
      if (candidates == null || candidates.isEmpty()) {
        return null;
      }
      instance = roundRobinPick(candidates);
    }
    catch (Exception e) {
      log.info(e, "Exception getting instance");
      return null;
    }

    if (instance == null) {
      log.error("No server instance found");
      return null;
    }

    return TO_SERVER.apply(instance);
  }

  private DruidServerMetadata roundRobinPick(List<DruidServerMetadata> candidates)
  {
    return candidates.get(roundRobinIdx.getAndIncrement() % candidates.size());
  }

  public Collection<Server> getAll()
  {
    try {
      return Collections2.transform(selectFunction.apply(druidServerDiscovery), TO_SERVER);
    }
    catch (Exception e) {
      log.info(e, "Unable to get all instances");
      return Collections.emptyList();
    }
  }

  private static class NoopSelectFunction implements Function<DruidServerDiscovery, List<DruidServerMetadata>>
  {
    @Override
    public List<DruidServerMetadata> apply(DruidServerDiscovery input)
    {
      return Collections.emptyList();
    }
  }
}
