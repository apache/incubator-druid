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

package org.apache.druid.extensions.watermarking;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import io.airlift.airline.Command;
import org.apache.druid.cli.ServerRunnable;
import org.apache.druid.guice.DruidProcessingModule;
import org.apache.druid.java.util.common.logger.Logger;

import java.util.List;

@Command(
    name = "watermark-keeper",
    description = "Runs a watermark keeper server, which exposes an api for querying watermark timeline "
                  + "metadata gathered by the watermark collector"
)
public class CliWatermarkKeeper extends ServerRunnable
{
  private static final Logger log = new Logger(CliWatermarkKeeper.class);

  public CliWatermarkKeeper()
  {
    super(log);
  }

  @Override
  protected List<? extends Module> getModules()
  {
    return ImmutableList.of(
        new DruidProcessingModule(),
        new WatermarkKeeperModule()
    );
  }
}
