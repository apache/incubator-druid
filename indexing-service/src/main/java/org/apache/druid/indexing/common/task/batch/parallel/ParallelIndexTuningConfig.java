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

package org.apache.druid.indexing.common.task.batch.parallel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.druid.indexing.common.task.IndexTuningConfig;
import org.apache.druid.segment.IndexSpec;
import org.apache.druid.segment.writeout.SegmentWriteOutMediumFactory;
import org.joda.time.Duration;
import org.joda.time.Period;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

@JsonTypeName("index_parallel")
public class ParallelIndexTuningConfig extends IndexTuningConfig
{
  private static final int DEFAULT_MAX_NUM_BATCH_TASKS = Integer.MAX_VALUE; // unlimited
  private static final int DEFAULT_MAX_RETRY = 3;
  private static final long DEFAULT_TASK_STATUS_CHECK_PERIOD_MS = 1000;

  private static final Duration DEFAULT_CHAT_HANDLER_TIMEOUT = new Period("PT10S").toStandardDuration();
  private static final int DEFAULT_CHAT_HANDLER_NUM_RETRIES = 5;

  private final int maxNumSubTasks;
  private final int maxRetry;
  private final long taskStatusCheckPeriodMs;

  private final Duration chatHandlerTimeout;
  private final int chatHandlerNumRetries;

  public static ParallelIndexTuningConfig defaultConfig()
  {
    return new ParallelIndexTuningConfig(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }

  @JsonCreator
  public ParallelIndexTuningConfig(
      @JsonProperty("targetPartitionSize") @Nullable Integer targetPartitionSize,
      @JsonProperty("maxRowsInMemory") @Nullable Integer maxRowsInMemory,
      @JsonProperty("maxBytesInMemory") @Nullable Long maxBytesInMemory,
      @JsonProperty("maxTotalRows") @Nullable Long maxTotalRows,
      @JsonProperty("numShards") @Nullable Integer numShards,
      @JsonProperty("indexSpec") @Nullable IndexSpec indexSpec,
      @JsonProperty("maxPendingPersists") @Nullable Integer maxPendingPersists,
      @JsonProperty("forceExtendableShardSpecs") @Nullable Boolean forceExtendableShardSpecs,
      @JsonProperty("forceGuaranteedRollup") @Nullable Boolean forceGuaranteedRollup,
      @JsonProperty("reportParseExceptions") @Nullable Boolean reportParseExceptions,
      @JsonProperty("pushTimeout") @Nullable Long pushTimeout,
      @JsonProperty("segmentWriteOutMediumFactory") @Nullable SegmentWriteOutMediumFactory segmentWriteOutMediumFactory,
      @JsonProperty("maxNumSubTasks") @Nullable Integer maxNumSubTasks,
      @JsonProperty("maxRetry") @Nullable Integer maxRetry,
      @JsonProperty("taskStatusCheckPeriodMs") @Nullable Long taskStatusCheckPeriodMs,
      @JsonProperty("chatHandlerTimeout") @Nullable Duration chatHandlerTimeout,
      @JsonProperty("chatHandlerNumRetries") @Nullable Integer chatHandlerNumRetries,
      @JsonProperty("logParseExceptions") @Nullable Boolean logParseExceptions,
      @JsonProperty("maxParseExceptions") @Nullable Integer maxParseExceptions,
      @JsonProperty("maxSavedParseExceptions") @Nullable Integer maxSavedParseExceptions,
      @JsonProperty("numFilesPerMerge") @Nullable Integer numFilesPerMerge
  )
  {
    super(
        targetPartitionSize,
        maxRowsInMemory,
        maxBytesInMemory,
        maxTotalRows,
        null,
        numShards,
        indexSpec,
        maxPendingPersists,
        null,
        forceExtendableShardSpecs,
        forceGuaranteedRollup,
        reportParseExceptions,
        null,
        pushTimeout,
        segmentWriteOutMediumFactory,
        logParseExceptions,
        maxParseExceptions,
        maxSavedParseExceptions,
        numFilesPerMerge
    );

    this.maxNumSubTasks = maxNumSubTasks == null ? DEFAULT_MAX_NUM_BATCH_TASKS : maxNumSubTasks;
    this.maxRetry = maxRetry == null ? DEFAULT_MAX_RETRY : maxRetry;
    this.taskStatusCheckPeriodMs = taskStatusCheckPeriodMs == null ?
                                   DEFAULT_TASK_STATUS_CHECK_PERIOD_MS :
                                   taskStatusCheckPeriodMs;

    this.chatHandlerTimeout = DEFAULT_CHAT_HANDLER_TIMEOUT;
    this.chatHandlerNumRetries = DEFAULT_CHAT_HANDLER_NUM_RETRIES;
  }

  @JsonProperty
  public int getMaxNumSubTasks()
  {
    return maxNumSubTasks;
  }

  @JsonProperty
  public int getMaxRetry()
  {
    return maxRetry;
  }

  @JsonProperty
  public long getTaskStatusCheckPeriodMs()
  {
    return taskStatusCheckPeriodMs;
  }

  @JsonProperty
  public Duration getChatHandlerTimeout()
  {
    return chatHandlerTimeout;
  }

  @JsonProperty
  public int getChatHandlerNumRetries()
  {
    return chatHandlerNumRetries;
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
    if (!super.equals(o)) {
      return false;
    }
    ParallelIndexTuningConfig that = (ParallelIndexTuningConfig) o;
    return maxNumSubTasks == that.maxNumSubTasks &&
           maxRetry == that.maxRetry &&
           taskStatusCheckPeriodMs == that.taskStatusCheckPeriodMs &&
           chatHandlerNumRetries == that.chatHandlerNumRetries &&
           Objects.equals(chatHandlerTimeout, that.chatHandlerTimeout);
  }

  @Override
  public int hashCode()
  {

    return Objects.hash(
        super.hashCode(),
        maxNumSubTasks,
        maxRetry,
        taskStatusCheckPeriodMs,
        chatHandlerTimeout,
        chatHandlerNumRetries
    );
  }

  public static class Builder
  {
    private Integer targetPartitionSize;
    private Integer maxRowsInMemory;
    private Long maxBytesInMemory;
    private Long maxTotalRows;
    private Integer numShards;
    private IndexSpec indexSpec;
    private File basePersistDirectory;
    private Integer maxPendingPersists;
    private Boolean forceExtendableShardSpecs;
    private Boolean forceGuaranteedRollup;
    private Boolean reportParseExceptions;
    private Long pushTimeout;
    private Boolean logParseExceptions;
    private Integer maxParseExceptions;
    private Integer maxSavedParseExceptions;
    private Integer numFilesPerMerge;
    private SegmentWriteOutMediumFactory segmentWriteOutMediumFactory;
    private Integer maxNumSubTasks;
    private Integer maxRetry;
    private Long taskStatusCheckPeriodMs;
    private Duration chatHandlerTimeout;
    private Integer chatHandlerNumRetries;

    public Builder setTargetPartitionSize(int targetPartitionSize)
    {
      this.targetPartitionSize = targetPartitionSize;
      return this;
    }

    public Builder setMaxRowsInMemory(int maxRowsInMemory)
    {
      this.maxRowsInMemory = maxRowsInMemory;
      return this;
    }

    public Builder setMaxBytesInMemory(long maxBytesInMemory)
    {
      this.maxBytesInMemory = maxBytesInMemory;
      return this;
    }

    public Builder setMaxTotalRows(long maxTotalRows)
    {
      this.maxTotalRows = maxTotalRows;
      return this;
    }

    public Builder setNumShards(int numShards)
    {
      this.numShards = numShards;
      return this;
    }

    public Builder setIndexSpec(IndexSpec indexSpec)
    {
      this.indexSpec = indexSpec;
      return this;
    }

    public Builder setBasePersistDirectory(File basePersistDirectory)
    {
      this.basePersistDirectory = basePersistDirectory;
      return this;
    }

    public Builder setMaxPendingPersists(int maxPendingPersists)
    {
      this.maxPendingPersists = maxPendingPersists;
      return this;
    }

    public Builder setForceExtendableShardSpecs(boolean forceExtendableShardSpecs)
    {
      this.forceExtendableShardSpecs = forceExtendableShardSpecs;
      return this;
    }

    public Builder setForceGuaranteedRollup(boolean forceGuaranteedRollup)
    {
      this.forceGuaranteedRollup = forceGuaranteedRollup;
      return this;
    }

    public Builder setReportParseExceptions(boolean reportParseExceptions)
    {
      this.reportParseExceptions = reportParseExceptions;
      return this;
    }

    public Builder setPushTimeout(long pushTimeout)
    {
      this.pushTimeout = pushTimeout;
      return this;
    }

    public Builder setLogParseExceptions(boolean logParseExceptions)
    {
      this.logParseExceptions = logParseExceptions;
      return this;
    }

    public Builder setMaxParseExceptions(int maxParseExceptions)
    {
      this.maxParseExceptions = maxParseExceptions;
      return this;
    }

    public Builder setMaxSavedParseExceptions(int maxSavedParseExceptions)
    {
      this.maxSavedParseExceptions = maxSavedParseExceptions;
      return this;
    }

    public Builder setNumFilesPerMerge(int numFilesPerMerge)
    {
      this.numFilesPerMerge = numFilesPerMerge;
      return this;
    }

    public Builder setSegmentWriteOutMediumFactory(SegmentWriteOutMediumFactory factory)
    {
      this.segmentWriteOutMediumFactory = factory;
      return this;
    }

    public Builder setMaxNumSubTasks(int maxNumSubTasks)
    {
      this.maxNumSubTasks = maxNumSubTasks;
      return this;
    }

    public Builder setMaxRetry(int maxRetry)
    {
      this.maxRetry = maxRetry;
      return this;
    }

    public Builder setTaskStatusCheckPeriodMs(long taskStatusCheckPeriodMs)
    {
      this.taskStatusCheckPeriodMs = taskStatusCheckPeriodMs;
      return this;
    }

    public Builder setChatHandlerTimeout(Duration chatHandlerTimeout)
    {
      this.chatHandlerTimeout = chatHandlerTimeout;
      return this;
    }

    public Builder setChatHandlerNumretries(int chatHandlerNumRetries)
    {
      this.chatHandlerNumRetries = chatHandlerNumRetries;
      return this;
    }

    public ParallelIndexTuningConfig build()
    {
      return new ParallelIndexTuningConfig(
          targetPartitionSize,
          maxRowsInMemory,
          maxBytesInMemory,
          maxTotalRows,
          numShards,
          indexSpec,
          maxPendingPersists,
          forceExtendableShardSpecs,
          forceGuaranteedRollup,
          null,
          pushTimeout,
          segmentWriteOutMediumFactory,
          maxNumSubTasks,
          maxRetry,
          taskStatusCheckPeriodMs,
          chatHandlerTimeout,
          chatHandlerNumRetries,
          logParseExceptions,
          maxParseExceptions,
          maxSavedParseExceptions,
          numFilesPerMerge
      );
    }

  }
}
