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

package io.druid.indexing.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TaskStatusWithReports extends TaskStatus
{
  @JsonProperty
  private Map<String, TaskReport> taskReports;

  @JsonCreator
  public TaskStatusWithReports(
      @JsonProperty("taskStatus") TaskStatus taskStatus,
      @JsonProperty("taskReports") Map<String, TaskReport> taskReports
  )
  {
    super(
        taskStatus.getId(),
        taskStatus.getStatusCode(),
        taskStatus.getDuration()
    );
    this.taskReports = taskReports;
  }

  @JsonProperty
  public Map<String, TaskReport> getTaskReports()
  {
    return taskReports;
  }

  @JsonIgnore
  public TaskStatus makeTaskStatusWithoutReports()
  {
    return new TaskStatus(
        getId(),
        getStatusCode(),
        getDuration()
    );
  }

  @Override
  public TaskStatus withDuration(long _duration)
  {
    return new TaskStatusWithReports(super.withDuration(_duration), taskReports);
  }
}
