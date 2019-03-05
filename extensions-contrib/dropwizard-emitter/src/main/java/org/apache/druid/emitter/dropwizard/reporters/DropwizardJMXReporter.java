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

package org.apache.druid.emitter.dropwizard.reporters;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.druid.emitter.dropwizard.DropwizardReporter;

import java.util.Objects;

@JsonTypeName("jmx")
public class DropwizardJMXReporter implements DropwizardReporter
{
  private String domain = "org.apache.druid";

  @JsonProperty
  public String getDomain()
  {
    return domain;
  }

  @Override
  public void start(MetricRegistry metricRegistry)
  {
    final JmxReporter reporter = JmxReporter.forRegistry(metricRegistry)
                                            .inDomain(domain).build();
    reporter.start();
  }

  @Override
  public void flush()
  {
  }

  @Override
  public void close()
  {

  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DropwizardJMXReporter that = (DropwizardJMXReporter) o;
    return Objects.equals(domain, that.domain);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(domain);
  }
}
