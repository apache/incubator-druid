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
package io.druid.emitter.ganglia;

import com.google.common.base.Preconditions;

import javax.validation.constraints.NotNull;

/**
 * Created by yangxuan on 2018/9/11.
 */
public class GangliaEvent
{
  private final String name;
  private final Number value;
  private final String service;

  GangliaEvent(@NotNull String name, Number value, @NotNull String service)
  {
    this.name = Preconditions.checkNotNull(name, "name can not be null");
    this.value = value;
    this.service = Preconditions.checkNotNull(service, "service can not be null");
  }

  public String getName()
  {
    return name;
  }

  public Number getValue()
  {
    return value;
  }

  public String getService()
  {
    return service;
  }
}
