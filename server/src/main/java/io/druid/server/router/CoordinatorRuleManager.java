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

package io.druid.server.router;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.metamx.http.client.response.FullResponseHolder;
import io.druid.concurrent.Execs;
import io.druid.discovery.DruidLeaderClient;
import io.druid.discovery.DruidLeaderClientProvider;
import io.druid.guice.ManageLifecycle;
import io.druid.guice.annotations.Json;
import io.druid.java.util.common.ISE;
import io.druid.java.util.common.concurrent.ScheduledExecutors;
import io.druid.java.util.common.lifecycle.LifecycleStart;
import io.druid.java.util.common.lifecycle.LifecycleStop;
import io.druid.java.util.common.logger.Logger;
import io.druid.server.coordinator.rules.Rule;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 */
@ManageLifecycle
public class CoordinatorRuleManager
{
  private static final Logger log = new Logger(CoordinatorRuleManager.class);

  private final ObjectMapper jsonMapper;
  private final Supplier<TieredBrokerConfig> config;

  private final AtomicReference<ConcurrentHashMap<String, List<Rule>>> rules;

  private final DruidLeaderClient druidLeaderClient;

  private volatile ScheduledExecutorService exec;

  private final Object lock = new Object();

  private volatile boolean started = false;

  @Inject
  public CoordinatorRuleManager(
      @Json ObjectMapper jsonMapper,
      Supplier<TieredBrokerConfig> config,
      DruidLeaderClientProvider druidLeaderClientProvider
  )
  {
    this.jsonMapper = jsonMapper;
    this.config = config;
    this.druidLeaderClient = druidLeaderClientProvider.get();

    this.rules = new AtomicReference<>(
        new ConcurrentHashMap<String, List<Rule>>()
    );
  }

  @LifecycleStart
  public void start()
  {
    synchronized (lock) {
      if (started) {
        return;
      }

      this.exec = Execs.scheduledSingleThreaded("CoordinatorRuleManager-Exec--%d");

      ScheduledExecutors.scheduleWithFixedDelay(
          exec,
          new Duration(0),
          config.get().getPollPeriod().toStandardDuration(),
          new Runnable()
          {
            @Override
            public void run()
            {
              poll();
            }
          }
      );

      started = true;
    }
  }

  @LifecycleStop
  public void stop()
  {
    synchronized (lock) {
      if (!started) {
        return;
      }

      rules.set(new ConcurrentHashMap<String, List<Rule>>());

      started = false;
      exec.shutdownNow();
      exec = null;
    }
  }

  public boolean isStarted()
  {
    return started;
  }

  @SuppressWarnings("unchecked")
  public void poll()
  {
    try {
      FullResponseHolder response = druidLeaderClient.go(
          druidLeaderClient.makeRequest(HttpMethod.GET, config.get().getRulesEndpoint())
      );

      if (!response.getStatus().equals(HttpResponseStatus.OK)) {
        throw new ISE(
            "Error while polling rules, status[%s] content[%s]",
            response.getStatus(),
            response.getContent()
        );
      }

      ConcurrentHashMap<String, List<Rule>> newRules = new ConcurrentHashMap<>(
          (Map<String, List<Rule>>) jsonMapper.readValue(
              response.getContent(), new TypeReference<Map<String, List<Rule>>>()
              {
              }
          )
      );

      log.info("Got [%,d] rules", newRules.size());

      rules.set(newRules);
    }
    catch (Exception e) {
      log.error(e, "Exception while polling for rules");
    }
  }

  public List<Rule> getRulesWithDefault(final String dataSource)
  {
    List<Rule> retVal = Lists.newArrayList();
    Map<String, List<Rule>> theRules = rules.get();
    if (theRules.get(dataSource) != null) {
      retVal.addAll(theRules.get(dataSource));
    }
    if (theRules.get(config.get().getDefaultRule()) != null) {
      retVal.addAll(theRules.get(config.get().getDefaultRule()));
    }
    return retVal;
  }
}
