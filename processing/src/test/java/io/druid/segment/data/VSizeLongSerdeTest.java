package io.druid.segment.data;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class VSizeLongSerdeTest
{
  private ByteBuffer buffer;
  private ByteArrayOutputStream out;
  private long values0 [] = {0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1};
  private long values1 [] = {0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1};
  private long values2 [] = {12, 5, 2, 9, 3, 2, 5, 1, 0, 6, 13, 10, 15};
  private long values3 [] = {1, 1, 1, 1, 1, 11, 11, 11, 11};
  private long values4 [] = {200, 200, 200, 401, 200, 301, 200, 200, 200, 404, 200, 200, 200, 200};
  private long values5 [] = {123, 632, 12, 39, 536, 0, 1023, 52, 777, 526, 214, 562, 823, 346};
  private long values6 [] = {1000000, 1000001, 1000002, 1000003, 1000004, 1000005, 1000006, 1000007, 1000008};

  @Before
  public void setUp () {
    out = new ByteArrayOutputStream();
  }

  @Test
  public void testGetBitsForMax () {
    Assert.assertEquals(1, VSizeLongSerde.getBitsForMax(1));
    Assert.assertEquals(1, VSizeLongSerde.getBitsForMax(2));
    Assert.assertEquals(2, VSizeLongSerde.getBitsForMax(3));
    Assert.assertEquals(4, VSizeLongSerde.getBitsForMax(16));
    Assert.assertEquals(8, VSizeLongSerde.getBitsForMax(200));
    Assert.assertEquals(12, VSizeLongSerde.getBitsForMax(999));
    Assert.assertEquals(24, VSizeLongSerde.getBitsForMax(12345678));
    Assert.assertEquals(32, VSizeLongSerde.getBitsForMax(Integer.MAX_VALUE));
    Assert.assertEquals(64, VSizeLongSerde.getBitsForMax(Long.MAX_VALUE));
  }

  @Test
  public void testSerdeValues () throws IOException
  {
    for (int i : VSizeLongSerde.SUPPORTED_SIZE) {
      testSerde(i, values0);
      if (i >= 1) {
        testSerde(i, values1);
      }
      if (i >= 4) {
        testSerde(i, values2);
        testSerde(i, values3);
      }
      if (i >= 9) {
        testSerde(i, values4);
      }
      if (i >= 10) {
        testSerde(i, values5);
      }
      if (i >= 20) {
        testSerde(i, values6);
      }
    }
  }

  @Test
  public void testSerdeLoop () throws IOException
  {
    for (int i : VSizeLongSerde.SUPPORTED_SIZE) {
      if (i >= 8) {
        testSerdeIncLoop(i, 0, 256);
      }
      if (i >= 16) {
        testSerdeIncLoop(i, 0, 50000);
      }
    }
  }

  public void testSerde (int longSize, long[] values) throws IOException
  {
    out.reset();
    out.write(1);
    out.write(2);
    VSizeLongSerde.LongSerializer ser = VSizeLongSerde.getSerializer(longSize, out);
    for (long value : values) {
      ser.write(value);
    }
    ser.close();
    buffer = ByteBuffer.wrap(out.toByteArray());
    Assert.assertEquals((values.length * longSize + 7) / 8, buffer.capacity() - 2);
    VSizeLongSerde.LongDeserializer de = VSizeLongSerde.getDeserializer(longSize, buffer, 2);
    for (int i = 0; i < values.length; i++) {
      Assert.assertEquals(values[i], de.get(i));
    }
  }

  public void testSerdeIncLoop (int longSize, long start, long end) throws IOException
  {
    out.reset();
    VSizeLongSerde.LongSerializer ser = VSizeLongSerde.getSerializer(longSize, out);
    for (long i = start; i < end; i++) {
      ser.write(i);
    }
    ser.close();
    buffer = ByteBuffer.wrap(out.toByteArray());
    Assert.assertEquals(((end - start) * longSize + 7) / 8, buffer.capacity());
    VSizeLongSerde.LongDeserializer de = VSizeLongSerde.getDeserializer(longSize, buffer, 0);
    for (int i = 0; i < end - start; i++) {
      Assert.assertEquals(start + i, de.get(i));
    }
  }


}
