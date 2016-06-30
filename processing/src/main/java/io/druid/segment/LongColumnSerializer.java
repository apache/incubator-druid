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

package io.druid.segment;

import io.druid.segment.data.CompressedLongsSupplierSerializer;
import io.druid.segment.data.CompressedObjectStrategy;
import io.druid.segment.data.GenericIndexedWriterFactory;
import io.druid.segment.data.IOPeon;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;

public class LongColumnSerializer implements GenericColumnSerializer
{
  public static LongColumnSerializer create(
      IOPeon ioPeon,
      String filenameBase,
      CompressedObjectStrategy.CompressionStrategy compression,
      GenericIndexedWriterFactory genericIndexedWriterFactory
  )
  {
    return new LongColumnSerializer(ioPeon, filenameBase, IndexIO.BYTE_ORDER, compression, genericIndexedWriterFactory);
  }

  private final IOPeon ioPeon;
  private final String filenameBase;
  private final ByteOrder byteOrder;
  private final CompressedObjectStrategy.CompressionStrategy compression;
  private final GenericIndexedWriterFactory genericIndexedWriterFactory;
  private CompressedLongsSupplierSerializer writer;

  public LongColumnSerializer(
      IOPeon ioPeon,
      String filenameBase,
      ByteOrder byteOrder,
      CompressedObjectStrategy.CompressionStrategy compression,
      GenericIndexedWriterFactory genericIndexedWriterFactory
  )
  {
    this.ioPeon = ioPeon;
    this.filenameBase = filenameBase;
    this.byteOrder = byteOrder;
    this.compression = compression;
    this.genericIndexedWriterFactory = genericIndexedWriterFactory;
  }

  @Override
  public void open() throws IOException
  {
    writer = CompressedLongsSupplierSerializer.create(
        ioPeon,
        String.format("%s.long_column", filenameBase),
        byteOrder,
        compression,
        genericIndexedWriterFactory
    );
    writer.open();
  }

  @Override
  public void serialize(Object obj) throws IOException
  {
    long val = (obj == null) ? 0 : ((Number) obj).longValue();
    writer.add(val);
  }

  @Override
  public void close() throws IOException
  {
    writer.close();
  }

  @Override
  public long getSerializedSize()
  {
    return writer.getSerializedSize();
  }

  @Override
  public void writeToChannel(WritableByteChannel channel) throws IOException
  {
    writer.writeToChannel(channel);
  }

}
