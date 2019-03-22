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

package org.apache.druid.indexing.seekablestream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import javax.validation.constraints.Min;

public class SeekableStreamSupervisorConfig
{
  @JsonProperty
  private boolean storingStackTraces = false;

  // The number of runs failed before the supervisor flips from a RUNNING to an UNHEALTHY state
  @JsonProperty
  @Min(3)
  private int unhealthinessThreshold = 3;

  // The number of successful before the supervisor flips from an UNHEALTHY to a RUNNING state
  @JsonProperty
  @Min(3)
  private int healthinessThreshold = 3;

  // The number of consecutive task failures before the supervisor flips from a RUNNING to an UNHEALTHY_TASKS state
  @JsonProperty
  @Min(3)
  private int taskUnhealthinessThreshold = 3;

  // The number of consecutive task successes before the supervisor flips from an UNHEALTHY_TASKS to a RUNNING state
  @JsonProperty
  @Min(3)
  private int taskHealthinessThreshold = 3;

  // The maximum number of exception events that can be returned through the supervisor status endpoint
  @JsonProperty
  private int maxStoredExceptionEvents = Math.max(unhealthinessThreshold, healthinessThreshold);

  public boolean isStoringStackTraces()
  {
    return storingStackTraces;
  }

  public int getUnhealthinessThreshold()
  {
    return unhealthinessThreshold;
  }

  public int getHealthinessThreshold()
  {
    return healthinessThreshold;
  }

  public int getTaskUnhealthinessThreshold()
  {
    return taskUnhealthinessThreshold;
  }

  public int getTaskHealthinessThreshold()
  {
    return taskHealthinessThreshold;
  }

  public int getMaxStoredExceptionEvents()
  {
    return maxStoredExceptionEvents;
  }

  @VisibleForTesting
  public void setMaxStoredExceptionEvents(int maxStoredExceptionEvents)
  {
    this.maxStoredExceptionEvents = maxStoredExceptionEvents;
  }
}
