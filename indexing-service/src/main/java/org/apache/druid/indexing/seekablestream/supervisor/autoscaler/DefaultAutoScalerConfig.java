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

package org.apache.druid.indexing.seekablestream.supervisor.autoscaler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

public class DefaultAutoScalerConfig implements AutoScalerConfig
{
  private final long metricsCollectionIntervalMillis;
  private final long metricsCollectionRangeMillis;
  private final long dynamicCheckStartDelayMillis;
  private final long dynamicCheckPeriod;
  private final long scaleOutThreshold;
  private final long scaleInThreshold;
  private final double triggerScaleOutThresholdFrequency;
  private final double triggerScaleInThresholdFrequency;
  private int taskCountMax;
  private int taskCountMin;
  private final int scaleInStep;
  private final int scaleOutStep;
  private final boolean enableTaskAutoscaler;
  private final String autoScalerStrategy;
  private final long minTriggerDynamicFrequencyMillis;

  @JsonCreator
  public DefaultAutoScalerConfig(
          @Nullable @JsonProperty("metricsCollectionIntervalMillis") Long metricsCollectionIntervalMillis,
          @Nullable @JsonProperty("metricsCollectionRangeMillis") Long metricsCollectionRangeMillis,
          @Nullable @JsonProperty("dynamicCheckStartDelayMillis") Long dynamicCheckStartDelayMillis,
          @Nullable @JsonProperty("dynamicCheckPeriod") Long dynamicCheckPeriod,
          @Nullable @JsonProperty("scaleOutThreshold") Long scaleOutThreshold,
          @Nullable @JsonProperty("scaleInThreshold") Long scaleInThreshold,
          @Nullable @JsonProperty("triggerScaleOutThresholdFrequency") Double triggerScaleOutThresholdFrequency,
          @Nullable @JsonProperty("triggerScaleInThresholdFrequency") Double triggerScaleInThresholdFrequency,
          @JsonProperty("taskCountMax") Integer taskCountMax,
          @JsonProperty("taskCountMin") Integer taskCountMin,
          @Nullable @JsonProperty("scaleInStep") Integer scaleInStep,
          @Nullable @JsonProperty("scaleOutStep") Integer scaleOutStep,
          @Nullable @JsonProperty("enableTaskAutoscaler") Boolean enableTaskAutoscaler,
          @Nullable @JsonProperty("autoScalerStrategy") String autoScalerStrategy,
          @Nullable @JsonProperty("minTriggerDynamicFrequencyMillis") Long minTriggerDynamicFrequencyMillis)
  {
    this.enableTaskAutoscaler = enableTaskAutoscaler != null ? enableTaskAutoscaler : false;
    this.metricsCollectionIntervalMillis = metricsCollectionIntervalMillis != null ? metricsCollectionIntervalMillis : 30000;
    this.metricsCollectionRangeMillis = metricsCollectionRangeMillis != null ? metricsCollectionRangeMillis : 600000;
    this.dynamicCheckStartDelayMillis = dynamicCheckStartDelayMillis != null ? dynamicCheckStartDelayMillis : 300000;
    this.dynamicCheckPeriod = dynamicCheckPeriod != null ? dynamicCheckPeriod : 60000;
    this.scaleOutThreshold = scaleOutThreshold != null ? scaleOutThreshold : 6000000;
    this.scaleInThreshold = scaleInThreshold != null ? scaleInThreshold : 1000000;
    this.triggerScaleOutThresholdFrequency = triggerScaleOutThresholdFrequency != null ? triggerScaleOutThresholdFrequency : 0.3;
    this.triggerScaleInThresholdFrequency = triggerScaleInThresholdFrequency != null ? triggerScaleInThresholdFrequency : 0.9;

    // Only do taskCountMax and taskCountMin check when autoscaler is enabled. So that users left autoConfig empty{} will not throw any exception and autoscaler is disabled.
    // If autoscaler is disabled, no matter what configs are set, they are not used.
    if (this.enableTaskAutoscaler) {
      if (taskCountMax == null || taskCountMin == null) {
        throw new RuntimeException("taskCountMax or taskCountMin can't be null!");
      } else if (taskCountMax < taskCountMin) {
        throw new RuntimeException("taskCountMax can't lower than taskCountMin!");
      }
      this.taskCountMax = taskCountMax;
      this.taskCountMin = taskCountMin;
    }

    this.scaleInStep = scaleInStep != null ? scaleInStep : 1;
    this.scaleOutStep = scaleOutStep != null ? scaleOutStep : 2;
    this.autoScalerStrategy = autoScalerStrategy != null ? autoScalerStrategy : "default";
    this.minTriggerDynamicFrequencyMillis = minTriggerDynamicFrequencyMillis != null ? minTriggerDynamicFrequencyMillis : 600000;
  }

  @JsonProperty
  public long getMetricsCollectionIntervalMillis()
  {
    return metricsCollectionIntervalMillis;
  }

  @JsonProperty
  public long getMetricsCollectionRangeMillis()
  {
    return metricsCollectionRangeMillis;
  }

  @JsonProperty
  public long getDynamicCheckStartDelayMillis()
  {
    return dynamicCheckStartDelayMillis;
  }

  @JsonProperty
  public long getDynamicCheckPeriod()
  {
    return dynamicCheckPeriod;
  }

  @JsonProperty
  public long getScaleOutThreshold()
  {
    return scaleOutThreshold;
  }

  @JsonProperty
  public long getScaleInThreshold()
  {
    return scaleInThreshold;
  }

  @JsonProperty
  public double getTriggerScaleOutThresholdFrequency()
  {
    return triggerScaleOutThresholdFrequency;
  }

  @JsonProperty
  public double getTriggerScaleInThresholdFrequency()
  {
    return triggerScaleInThresholdFrequency;
  }

  @Override
  @JsonProperty
  public int getTaskCountMax()
  {
    return taskCountMax;
  }

  @Override
  @JsonProperty
  public int getTaskCountMin()
  {
    return taskCountMin;
  }

  @JsonProperty
  public int getScaleInStep()
  {
    return scaleInStep;
  }

  @JsonProperty
  public int getScaleOutStep()
  {
    return scaleOutStep;
  }

  @Override
  @JsonProperty
  public boolean getEnableTaskAutoscaler()
  {
    return enableTaskAutoscaler;
  }

  @Override
  @JsonProperty
  public String getAutoScalerStrategy()
  {
    return autoScalerStrategy;
  }

  @Override
  @JsonProperty
  public long getMinTriggerDynamicFrequencyMillis()
  {
    return minTriggerDynamicFrequencyMillis;
  }
}
