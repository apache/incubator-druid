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

package org.apache.druid.client.indexing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * IOConfig for {@link ClientCompactionTaskQuery}.
 *
 * Should be synchronized with org.apache.druid.indexing.common.task.CompactionIOConfig.
 */
public class ClientCompactionIOConfig
{
  private static final String TYPE = "compact";

  private final ClientCompactionIntervalSpec inputSpec;

  @JsonCreator
  public ClientCompactionIOConfig(@JsonProperty("inputSpec") ClientCompactionIntervalSpec inputSpec)
  {
    this.inputSpec = inputSpec;
  }

  @JsonProperty
  public String getType()
  {
    return TYPE;
  }

  @JsonProperty
  public ClientCompactionIntervalSpec getInputSpec()
  {
    return inputSpec;
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
    ClientCompactionIOConfig that = (ClientCompactionIOConfig) o;
    return Objects.equals(inputSpec, that.inputSpec);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(inputSpec);
  }

  @Override
  public String toString()
  {
    return "ClientCompactionIOConfig{" +
           "inputSpec=" + inputSpec +
           '}';
  }
}
