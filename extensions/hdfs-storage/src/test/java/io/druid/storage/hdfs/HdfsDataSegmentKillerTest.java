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

package io.druid.storage.hdfs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.druid.timeline.DataSegment;
import io.druid.timeline.partition.NoneShardSpec;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.joda.time.Interval;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/**
 */
public class HdfsDataSegmentKillerTest
{

  private static final String DATA_SOURCE = "dataSource";

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testKillWithOldStyleSegmentPaths() throws Exception
  {
    Configuration config = new Configuration();
    HdfsDataSegmentKiller killer = new HdfsDataSegmentKiller(config);

    FileSystem fs = FileSystem.get(config);

    // Create following segments and then delete them in this order and assert directory deletions
    // /tmp/dataSource/interval1/v1/0/index.zip
    // /tmp/dataSource/interval1/v1/1/index.zip
    // /tmp/dataSource/interval1/v2/0/index.zip
    // /tmp/dataSource/interval2/v1/0/index.zip

    Path dataSourceDir = new Path(temporaryFolder.newFolder(DATA_SOURCE).toString());

    Path interval1Dir = new Path(dataSourceDir, "interval1");
    Path version11Dir = new Path(interval1Dir, "v1");
    Path partition011Dir = new Path(version11Dir, "0");
    Path partition111Dir = new Path(version11Dir, "1");

    makePartitionDirWithIndex(fs, partition011Dir);
    makePartitionDirWithIndex(fs, partition111Dir);

    Path version21Dir = new Path(interval1Dir, "v2");
    Path partition021Dir = new Path(version21Dir, "0");

    makePartitionDirWithIndex(fs, partition021Dir);

    Path interval2Dir = new Path(dataSourceDir, "interval2");
    Path version12Dir = new Path(interval2Dir, "v1");
    Path partition012Dir = new Path(version12Dir, "0");

    makePartitionDirWithIndex(fs, partition012Dir);

    killer.kill(getSegmentWithPath(new Path(partition011Dir, "index.zip").toString()));

    Assert.assertFalse(fs.exists(partition011Dir));
    Assert.assertTrue(fs.exists(partition111Dir));
    Assert.assertTrue(fs.exists(partition021Dir));
    Assert.assertTrue(fs.exists(partition012Dir));

    killer.kill(getSegmentWithPath(new Path(partition111Dir, "index.zip").toString()));

    Assert.assertFalse(fs.exists(version11Dir));
    Assert.assertTrue(fs.exists(partition021Dir));
    Assert.assertTrue(fs.exists(partition012Dir));

    killer.kill(getSegmentWithPath(new Path(partition021Dir, "index.zip").toString()));

    Assert.assertFalse(fs.exists(interval1Dir));
    Assert.assertTrue(fs.exists(partition012Dir));

    killer.kill(getSegmentWithPath(new Path(partition012Dir, "index.zip").toString()));

    Assert.assertFalse(fs.exists(dataSourceDir));
  }

  @Test
  public void testKillWithNewStyleSegmentPath() throws Exception
  {
    Configuration config = new Configuration();
    HdfsDataSegmentKiller killer = new HdfsDataSegmentKiller(config);

    FileSystem fs = FileSystem.get(config);

    // Create following segments with new storage path format and ensure that deletion of
    // segment does not result in deletion of dataSource directory
    // /tmp/dataSource/interval_version_20/index.zip

    Path dataSourceDir = new Path(temporaryFolder.newFolder(DATA_SOURCE).toString());
    Path newSegmentStorageDir = new Path(dataSourceDir, "interval_version_20");
    makePartitionDirWithIndex(fs, newSegmentStorageDir);

    Assert.assertTrue(fs.exists(newSegmentStorageDir));

    killer.kill(getSegmentWithPath(new Path(newSegmentStorageDir, "index.zip").toString()));

    Assert.assertFalse(fs.exists(newSegmentStorageDir));
    Assert.assertTrue(fs.exists(dataSourceDir));
  }

  private void makePartitionDirWithIndex(FileSystem fs, Path path) throws IOException
  {
    Assert.assertTrue(fs.mkdirs(path));
    try (FSDataOutputStream os = fs.create(new Path(path, "index.zip"))) {
    }
  }

  private DataSegment getSegmentWithPath(String path)
  {
    return new DataSegment(
        DATA_SOURCE,
        Interval.parse("2000/3000"),
        "ver",
        ImmutableMap.<String, Object>of(
            "type", "hdfs",
            "path", path
        ),
        ImmutableList.of("product"),
        ImmutableList.of("visited_sum", "unique_hosts"),
        new NoneShardSpec(),
        9,
        12334
    );
  }
}
