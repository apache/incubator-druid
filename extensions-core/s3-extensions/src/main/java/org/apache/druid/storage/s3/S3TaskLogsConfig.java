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

package org.apache.druid.storage.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

/**
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3TaskLogsConfig
{
  @JsonProperty
  @NotNull
  private String s3Bucket = null;

  @JsonProperty
  @NotNull
  private String s3Prefix = null;

  @JsonProperty
  private boolean disableAcl = false;

  public void setDisableAcl(boolean disableAcl)
  {
    this.disableAcl = disableAcl;
  }

  public String getS3Bucket()
  {
    return s3Bucket;
  }

  public String getS3Prefix()
  {
    return s3Prefix;
  }

  public boolean getDisableAcl()
  {
    return disableAcl;
  }
}
