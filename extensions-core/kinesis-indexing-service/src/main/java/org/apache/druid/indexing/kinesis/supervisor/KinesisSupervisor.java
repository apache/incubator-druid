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

package org.apache.druid.indexing.kinesis.supervisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.druid.common.aws.AWSCredentialsConfig;
import org.apache.druid.indexing.common.stats.RowIngestionMetersFactory;
import org.apache.druid.indexing.common.task.Task;
import org.apache.druid.indexing.common.task.TaskResource;
import org.apache.druid.indexing.kinesis.KinesisDataSourceMetadata;
import org.apache.druid.indexing.kinesis.KinesisIndexTask;
import org.apache.druid.indexing.kinesis.KinesisIndexTaskClientFactory;
import org.apache.druid.indexing.kinesis.KinesisIndexTaskIOConfig;
import org.apache.druid.indexing.kinesis.KinesisIndexTaskTuningConfig;
import org.apache.druid.indexing.kinesis.KinesisRecordSupplier;
import org.apache.druid.indexing.kinesis.KinesisSequenceNumber;
import org.apache.druid.indexing.overlord.DataSourceMetadata;
import org.apache.druid.indexing.overlord.IndexerMetadataStorageCoordinator;
import org.apache.druid.indexing.overlord.TaskMaster;
import org.apache.druid.indexing.overlord.TaskStorage;
import org.apache.druid.indexing.seekablestream.SeekableStreamDataSourceMetadata;
import org.apache.druid.indexing.seekablestream.SeekableStreamEndSequenceNumbers;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTask;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskIOConfig;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskTuningConfig;
import org.apache.druid.indexing.seekablestream.SeekableStreamSequenceNumbers;
import org.apache.druid.indexing.seekablestream.SeekableStreamStartSequenceNumbers;
import org.apache.druid.indexing.seekablestream.common.OrderedSequenceNumber;
import org.apache.druid.indexing.seekablestream.common.RecordSupplier;
import org.apache.druid.indexing.seekablestream.common.StreamPartition;
import org.apache.druid.indexing.seekablestream.supervisor.SeekableStreamSupervisor;
import org.apache.druid.indexing.seekablestream.supervisor.SeekableStreamSupervisorIOConfig;
import org.apache.druid.indexing.seekablestream.supervisor.SeekableStreamSupervisorReportPayload;
import org.apache.druid.indexing.seekablestream.utils.RandomIdUtils;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.emitter.EmittingLogger;
import org.joda.time.DateTime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Supervisor responsible for managing the KinesisIndexTask for a single dataSource. At a high level, the class accepts a
 * {@link KinesisSupervisorSpec} which includes the Kinesis stream and configuration as well as an ingestion spec which will
 * be used to generate the indexing tasks. The run loop periodically refreshes its view of the Kinesis stream's partitions
 * and the list of running indexing tasks and ensures that all partitions are being read from and that there are enough
 * tasks to satisfy the desired number of replicas. As tasks complete, new tasks are queued to process the next range of
 * Kinesis sequences.
 * <p>
 * the Kinesis supervisor does not yet support lag calculations
 */
public class KinesisSupervisor extends SeekableStreamSupervisor<String, String>
{
  private static final HashFunction HASH_FUNCTION = Hashing.sha1();

  private static final EmittingLogger log = new EmittingLogger(KinesisSupervisor.class);

  public static final TypeReference<TreeMap<Integer, Map<String, String>>> CHECKPOINTS_TYPE_REF =
      new TypeReference<TreeMap<Integer, Map<String, String>>>()
      {
      };

  private static final String NOT_SET = "-1";
  private final KinesisSupervisorSpec spec;
  private final AWSCredentialsConfig awsCredentialsConfig;

  public KinesisSupervisor(
      final TaskStorage taskStorage,
      final TaskMaster taskMaster,
      final IndexerMetadataStorageCoordinator indexerMetadataStorageCoordinator,
      final KinesisIndexTaskClientFactory taskClientFactory,
      final ObjectMapper mapper,
      final KinesisSupervisorSpec spec,
      final RowIngestionMetersFactory rowIngestionMetersFactory,
      final AWSCredentialsConfig awsCredentialsConfig
  )
  {
    super(
        StringUtils.format("KinesisSupervisor-%s", spec.getDataSchema().getDataSource()),
        taskStorage,
        taskMaster,
        indexerMetadataStorageCoordinator,
        taskClientFactory,
        mapper,
        spec,
        rowIngestionMetersFactory,
        true
    );

    this.spec = spec;
    this.awsCredentialsConfig = awsCredentialsConfig;
  }

  @Override
  protected SeekableStreamIndexTaskIOConfig createTaskIoConfig(
      int groupId,
      Map<String, String> startPartitions,
      Map<String, String> endPartitions,
      String baseSequenceName,
      DateTime minimumMessageTime,
      DateTime maximumMessageTime,
      Set<String> exclusiveStartSequenceNumberPartitions,
      SeekableStreamSupervisorIOConfig ioConfigg
  )
  {
    KinesisSupervisorIOConfig ioConfig = (KinesisSupervisorIOConfig) ioConfigg;
    return new KinesisIndexTaskIOConfig(
        groupId,
        baseSequenceName,
        new SeekableStreamStartSequenceNumbers<>(
            ioConfig.getStream(),
            startPartitions,
            exclusiveStartSequenceNumberPartitions
        ),
        new SeekableStreamEndSequenceNumbers<>(ioConfig.getStream(), endPartitions),
        true,
        minimumMessageTime,
        maximumMessageTime,
        ioConfig.getEndpoint(),
        ioConfig.getRecordsPerFetch(),
        ioConfig.getFetchDelayMillis(),
        ioConfig.getAwsAssumedRoleArn(),
        ioConfig.getAwsExternalId(),
        ioConfig.isDeaggregate()
    );
  }

  @Override
  protected List<SeekableStreamIndexTask<String, String>> createIndexTasks(
      int replicas,
      String baseSequenceName,
      ObjectMapper sortingMapper,
      TreeMap<Integer, Map<String, String>> sequenceOffsets,
      SeekableStreamIndexTaskIOConfig taskIoConfig,
      SeekableStreamIndexTaskTuningConfig taskTuningConfig,
      RowIngestionMetersFactory rowIngestionMetersFactory
  ) throws JsonProcessingException
  {
    final String checkpoints = sortingMapper.writerFor(CHECKPOINTS_TYPE_REF).writeValueAsString(sequenceOffsets);
    final Map<String, Object> context = createBaseTaskContexts();
    context.put(CHECKPOINTS_CTX_KEY, checkpoints);

    List<SeekableStreamIndexTask<String, String>> taskList = new ArrayList<>();
    for (int i = 0; i < replicas; i++) {
      String taskId = Joiner.on("_").join(baseSequenceName, RandomIdUtils.getRandomId());
      taskList.add(new KinesisIndexTask(
          taskId,
          new TaskResource(baseSequenceName, 1),
          spec.getDataSchema(),
          (KinesisIndexTaskTuningConfig) taskTuningConfig,
          (KinesisIndexTaskIOConfig) taskIoConfig,
          context,
          null,
          null,
          rowIngestionMetersFactory,
          awsCredentialsConfig,
          null
      ));
    }
    return taskList;
  }


  @Override
  protected RecordSupplier<String, String> setupRecordSupplier()
      throws RuntimeException
  {
    KinesisSupervisorIOConfig ioConfig = spec.getIoConfig();
    KinesisIndexTaskTuningConfig taskTuningConfig = spec.getTuningConfig();

    return new KinesisRecordSupplier(
        KinesisRecordSupplier.getAmazonKinesisClient(
            ioConfig.getEndpoint(),
            awsCredentialsConfig,
            ioConfig.getAwsAssumedRoleArn(),
            ioConfig.getAwsExternalId()
        ),
        ioConfig.getRecordsPerFetch(),
        ioConfig.getFetchDelayMillis(),
        1,
        ioConfig.isDeaggregate(),
        taskTuningConfig.getRecordBufferSize(),
        taskTuningConfig.getRecordBufferOfferTimeout(),
        taskTuningConfig.getRecordBufferFullWait(),
        taskTuningConfig.getFetchSequenceNumberTimeout(),
        taskTuningConfig.getMaxRecordsPerPoll()
    );
  }

  @Override
  protected void scheduleReporting(ScheduledExecutorService reportingExec)
  {
    // not yet implemented, see issue #6739
  }

  /**
   * We try to parse the shard number of the shard ID, using a BigInteger because the Kinesis shard ID can be
   * up to 128 characters. The shard number is used preferentially because it provides a fixed and easily predictable
   * mapping from shard to task group number.
   *
   * If we can't parse the shard number from the ID, then we fall back to hashing the shard ID string.
   */
  @Override
  protected int getTaskGroupIdForPartition(String partitionId)
  {
    if (!partitionIds.contains(partitionId)) {
      partitionIds.add(partitionId);
    }

    return Math.abs(getHashIntFromShardId(partitionId) % spec.getIoConfig().getTaskCount());
  }

  private int getHashIntFromShardId(String shardId)
  {
    HashCode hashCode = HASH_FUNCTION.hashString(shardId, StandardCharsets.UTF_8);
    return hashCode.asInt();
  }

  @Override
  protected boolean checkSourceMetadataMatch(DataSourceMetadata metadata)
  {
    return metadata instanceof KinesisDataSourceMetadata;
  }

  @Override
  protected boolean doesTaskTypeMatchSupervisor(Task task)
  {
    return task instanceof KinesisIndexTask;
  }

  @Override
  protected SeekableStreamSupervisorReportPayload<String, String> createReportPayload(
      int numPartitions,
      boolean includeOffsets
  )
  {
    KinesisSupervisorIOConfig ioConfig = spec.getIoConfig();
    return new KinesisSupervisorReportPayload(
        spec.getDataSchema().getDataSource(),
        ioConfig.getStream(),
        numPartitions,
        ioConfig.getReplicas(),
        ioConfig.getTaskDuration().getMillis() / 1000,
        spec.isSuspended(),
        stateManager.isHealthy(),
        stateManager.getSupervisorState().getBasicState(),
        stateManager.getSupervisorState(),
        stateManager.getExceptionEvents()
    );
  }

  // not yet supported, will be implemented in the future
  @Override
  protected Map<String, String> getLagPerPartition(Map<String, String> currentOffsets)
  {
    return ImmutableMap.of();
  }

  @Override
  protected SeekableStreamDataSourceMetadata<String, String> createDataSourceMetaDataForReset(
      String stream,
      Map<String, String> map
  )
  {
    return new KinesisDataSourceMetadata(new SeekableStreamEndSequenceNumbers<>(stream, map));
  }

  @Override
  protected OrderedSequenceNumber<String> makeSequenceNumber(String seq, boolean isExclusive)
  {
    return KinesisSequenceNumber.of(seq, isExclusive);
  }

  @Override
  protected void updateLatestSequenceFromStream(
      RecordSupplier<String, String> recordSupplier, Set<StreamPartition<String>> streamPartitions
  )
  {
    // do nothing
  }

  @Override
  protected String baseTaskName()
  {
    return "index_kinesis";
  }

  @Override
  protected String getNotSetMarker()
  {
    return NOT_SET;
  }

  @Override
  protected String getEndOfPartitionMarker()
  {
    return KinesisSequenceNumber.NO_END_SEQUENCE_NUMBER;
  }

  @Override
  protected boolean isEndOfShard(String seqNum)
  {
    return KinesisSequenceNumber.END_OF_SHARD_MARKER.equals(seqNum);
  }

  @Override
  protected boolean useExclusiveStartSequenceNumberForNonFirstSequence()
  {
    return true;
  }

  @Override
  protected Map<String, OrderedSequenceNumber<String>> filterExpiredPartitionsFromStartingOffsets(
      Map<String, OrderedSequenceNumber<String>> startingOffsets
  )
  {
    Map<String, OrderedSequenceNumber<String>> filteredOffsets = new HashMap<>();
    for (Map.Entry<String, OrderedSequenceNumber<String>> entry : startingOffsets.entrySet()) {
      if (!entry.getValue().get().equals(KinesisSequenceNumber.END_OF_SHARD_MARKER)) {
        filteredOffsets.put(entry.getKey(), entry.getValue());
      } else {
        log.debug("Excluding shard[%s] because it has reached EOS.", entry.getKey());
      }
    }
    return filteredOffsets;
  }

  @Override
  protected boolean supportsPartitionExpiration()
  {
    return true;
  }

  @Override
  protected KinesisDataSourceMetadata createDataSourceMetadataWithoutExpiredPartitions(
      SeekableStreamDataSourceMetadata<String, String> currentMetadata,
      Set<String> expiredPartitionIds
  )
  {
    log.info("Cleaning up dead shards: " + expiredPartitionIds);

    final KinesisDataSourceMetadata dataSourceMetadata = (KinesisDataSourceMetadata) currentMetadata;

    SeekableStreamSequenceNumbers<String, String> old = dataSourceMetadata.getSeekableStreamSequenceNumbers();

    Map<String, String> oldPartitionSequenceNumberMap = old.getPartitionSequenceNumberMap();
    Map<String, String> newPartitionSequenceNumberMap = new HashMap<>();
    for (Map.Entry<String, String> entry : oldPartitionSequenceNumberMap.entrySet()) {
      if (!expiredPartitionIds.contains(entry.getKey())) {
        newPartitionSequenceNumberMap.put(entry.getKey(), entry.getValue());
      }
    }

    Set<String> oldExclusiveStartPartitions = null;
    Set<String> newExclusiveStartPartitions = null;
    if (old instanceof SeekableStreamStartSequenceNumbers) {
      newExclusiveStartPartitions = new HashSet<>();
      oldExclusiveStartPartitions = ((SeekableStreamStartSequenceNumbers<String, String>) old).getExclusivePartitions();
      for (String partitionId : oldExclusiveStartPartitions) {
        if (!expiredPartitionIds.contains(partitionId)) {
          newExclusiveStartPartitions.add(partitionId);
        }
      }
    }

    SeekableStreamSequenceNumbers<String, String> newSequences;
    if (old instanceof SeekableStreamStartSequenceNumbers) {
      newSequences = new SeekableStreamStartSequenceNumbers<String, String>(
          old.getStream(),
          null,
          newPartitionSequenceNumberMap,
          null,
          newExclusiveStartPartitions
      );
    } else {
      newSequences = new SeekableStreamEndSequenceNumbers<String, String>(
          old.getStream(),
          null,
          newPartitionSequenceNumberMap,
          null
      );
    }

    return new KinesisDataSourceMetadata(newSequences);
  }
}
