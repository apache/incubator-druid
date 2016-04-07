/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
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

package io.druid.query.lookup;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.metamx.common.StringUtils;
import io.druid.jackson.DefaultObjectMapper;
import io.druid.query.extraction.MapLookupExtractorFactory;
import io.druid.server.namespace.cache.NamespaceExtractionCacheManager;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.consumer.TopicFilter;
import kafka.javaapi.consumer.ConsumerConnector;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static io.druid.query.lookup.KafkaLookupExtractorFactory.DEFAULT_STRING_DECODER;

public class KafkaLookupExtractorFactoryTest
{
  private static final String TOPIC = "some_topic";
  private static final Map<String, String> DEFAULT_PROPERTIES = ImmutableMap.of(
      "some.property", "some.value"
  );
  private final ObjectMapper mapper = new DefaultObjectMapper();
  final NamespaceExtractionCacheManager cacheManager = EasyMock.createStrictMock(NamespaceExtractionCacheManager.class);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp()
  {
    mapper.setInjectableValues(new InjectableValues()
    {
      @Override
      public Object findInjectableValue(
          Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance
      )
      {
        if ("io.druid.server.namespace.cache.NamespaceExtractionCacheManager".equals(valueId)) {
          return cacheManager;
        } else {
          return null;
        }
      }
    });
  }

  @Test
  public void testSimpleSerDe() throws Exception
  {
    final KafkaLookupExtractorFactory expected = new KafkaLookupExtractorFactory(null, TOPIC, DEFAULT_PROPERTIES);
    final KafkaLookupExtractorFactory result = mapper.readValue(
        mapper.writeValueAsString(expected),
        KafkaLookupExtractorFactory.class
    );
    Assert.assertEquals(expected.getKafkaTopic(), result.getKafkaTopic());
    Assert.assertEquals(expected.getKafkaProperties(), result.getKafkaProperties());
    Assert.assertEquals(cacheManager, result.getCacheManager());
    Assert.assertEquals(0, expected.getCompletedEventCount());
    Assert.assertEquals(0, result.getCompletedEventCount());
  }

  @Test
  public void testCacheKeyScramblesOnNewData()
  {
    final int n = 1000;
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    factory.getMapRef().set(ImmutableMap.<String, String>of());
    final AtomicLong events = factory.getDoubleEventCount();

    final LookupExtractor extractor = factory.get();

    final List<byte[]> byteArrays = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      final byte[] myKey = extractor.getCacheKey();
      // Not terribly efficient.. but who cares
      for (byte[] byteArray : byteArrays) {
        Assert.assertFalse(Arrays.equals(byteArray, myKey));
      }
      byteArrays.add(myKey);
      events.incrementAndGet();
    }
    Assert.assertEquals(n, byteArrays.size());
  }

  @Test
  public void testCacheKeyScramblesDifferentStarts()
  {
    final int n = 1000;
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    factory.getMapRef().set(ImmutableMap.<String, String>of());
    final AtomicLong events = factory.getDoubleEventCount();

    final List<byte[]> byteArrays = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      final LookupExtractor extractor = factory.get();
      final byte[] myKey = extractor.getCacheKey();
      // Not terribly efficient.. but who cares
      for (byte[] byteArray : byteArrays) {
        Assert.assertFalse(Arrays.equals(byteArray, myKey));
      }
      byteArrays.add(myKey);
      events.incrementAndGet();
    }
    Assert.assertEquals(n, byteArrays.size());
  }

  @Test
  public void testCacheKeySameOnNoChange()
  {
    final int n = 1000;
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    factory.getMapRef().set(ImmutableMap.<String, String>of());

    final LookupExtractor extractor = factory.get();

    final byte[] baseKey = extractor.getCacheKey();
    for (int i = 0; i < n; ++i) {
      Assert.assertArrayEquals(baseKey, factory.get().getCacheKey());
    }
  }

  @Test
  public void testCacheKeyDifferentForTopics()
  {
    final KafkaLookupExtractorFactory factory1 = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    factory1.getMapRef().set(ImmutableMap.<String, String>of());
    final KafkaLookupExtractorFactory factory2 = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC + "b",
        DEFAULT_PROPERTIES
    );
    factory2.getMapRef().set(ImmutableMap.<String, String>of());

    Assert.assertFalse(Arrays.equals(factory1.get().getCacheKey(), factory2.get().getCacheKey()));
  }

  @Test
  public void testReplaces()
  {
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    Assert.assertTrue(factory.replaces(new MapLookupExtractorFactory(ImmutableMap.<String, String>of(), false)));
    Assert.assertFalse(factory.replaces(factory));
    Assert.assertFalse(factory.replaces(new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    )));
    Assert.assertTrue(factory.replaces(new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC + "b",
        DEFAULT_PROPERTIES
    )));
    Assert.assertTrue(factory.replaces(new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("some.property", "some.other.value")
    )));
    Assert.assertTrue(factory.replaces(new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("some.other.property", "some.value")
    )));
  }

  @Test
  public void testStopWithoutStart()
  {
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    );
    Assert.assertFalse(factory.close());
  }

  @Test
  public void testStartStop()
  {
    final KafkaStream<String, String> kafkaStream = EasyMock.createStrictMock(KafkaStream.class);
    final ConsumerIterator<String, String> consumerIterator = EasyMock.createStrictMock(ConsumerIterator.class);
    final ConsumerConnector consumerConnector = EasyMock.createStrictMock(ConsumerConnector.class);
    EasyMock.expect(consumerConnector.createMessageStreamsByFilter(
        EasyMock.anyObject(TopicFilter.class),
        EasyMock.anyInt(),
        EasyMock.eq(
            DEFAULT_STRING_DECODER),
        EasyMock.eq(DEFAULT_STRING_DECODER)
    )).andReturn(ImmutableList.of(kafkaStream)).once();
    EasyMock.expect(kafkaStream.iterator()).andReturn(consumerIterator).once();
    EasyMock.expect(consumerIterator.hasNext()).andReturn(false).anyTimes();
    EasyMock.expect(cacheManager.getCacheMap(EasyMock.anyString()))
            .andReturn(new ConcurrentHashMap<String, String>())
            .once();
    EasyMock.expect(cacheManager.delete(EasyMock.anyString())).andReturn(true).once();
    consumerConnector.shutdown();
    EasyMock.expectLastCall().once();
    EasyMock.replay(cacheManager, kafkaStream, consumerConnector, consumerIterator);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("zookeeper.connect", "localhost")
    )
    {
      @Override
      ConsumerConnector buildConnector(Properties properties)
      {
        return consumerConnector;
      }
    };
    Assert.assertTrue(factory.start());
    Assert.assertTrue(factory.close());
    EasyMock.verify(cacheManager, kafkaStream, consumerConnector, consumerIterator);
  }

  @Test
  public void testStopDeleteError()
  {
    final KafkaStream<String, String> kafkaStream = EasyMock.createStrictMock(KafkaStream.class);
    final ConsumerIterator<String, String> consumerIterator = EasyMock.createStrictMock(ConsumerIterator.class);
    final ConsumerConnector consumerConnector = EasyMock.createStrictMock(ConsumerConnector.class);
    EasyMock.expect(consumerConnector.createMessageStreamsByFilter(
        EasyMock.anyObject(TopicFilter.class),
        EasyMock.anyInt(),
        EasyMock.eq(
            DEFAULT_STRING_DECODER),
        EasyMock.eq(DEFAULT_STRING_DECODER)
    )).andReturn(ImmutableList.of(kafkaStream)).once();
    EasyMock.expect(kafkaStream.iterator()).andReturn(consumerIterator).once();
    EasyMock.expect(consumerIterator.hasNext()).andReturn(false).anyTimes();
    EasyMock.expect(cacheManager.getCacheMap(EasyMock.anyString()))
            .andReturn(new ConcurrentHashMap<String, String>())
            .once();
    EasyMock.expect(cacheManager.delete(EasyMock.anyString())).andReturn(false).once();

    EasyMock.replay(cacheManager, kafkaStream, consumerConnector, consumerIterator);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("zookeeper.connect", "localhost")
    )
    {
      @Override
      ConsumerConnector buildConnector(Properties properties)
      {
        return consumerConnector;
      }
    };
    Assert.assertTrue(factory.start());
    Assert.assertFalse(factory.close());
    EasyMock.verify(cacheManager, kafkaStream, consumerConnector, consumerIterator);
  }


  @Test
  public void testStartStopStart()
  {
    final KafkaStream<String, String> kafkaStream = EasyMock.createStrictMock(KafkaStream.class);
    final ConsumerIterator<String, String> consumerIterator = EasyMock.createStrictMock(ConsumerIterator.class);
    final ConsumerConnector consumerConnector = EasyMock.createStrictMock(ConsumerConnector.class);
    EasyMock.expect(consumerConnector.createMessageStreamsByFilter(
        EasyMock.anyObject(TopicFilter.class),
        EasyMock.anyInt(),
        EasyMock.eq(
            DEFAULT_STRING_DECODER),
        EasyMock.eq(DEFAULT_STRING_DECODER)
    )).andReturn(ImmutableList.of(kafkaStream)).once();
    EasyMock.expect(kafkaStream.iterator()).andReturn(consumerIterator).once();
    EasyMock.expect(consumerIterator.hasNext()).andReturn(false).anyTimes();
    EasyMock.expect(cacheManager.getCacheMap(EasyMock.anyString()))
            .andReturn(new ConcurrentHashMap<String, String>())
            .once();
    EasyMock.expect(cacheManager.delete(EasyMock.anyString())).andReturn(true).once();
    consumerConnector.shutdown();
    EasyMock.expectLastCall().once();
    EasyMock.replay(cacheManager, kafkaStream, consumerConnector, consumerIterator);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("zookeeper.connect", "localhost")
    )
    {
      @Override
      ConsumerConnector buildConnector(Properties properties)
      {
        return consumerConnector;
      }
    };
    Assert.assertTrue(factory.start());
    Assert.assertTrue(factory.close());
    Assert.assertFalse(factory.start());
    EasyMock.verify(cacheManager, kafkaStream, consumerConnector, consumerIterator);
  }

  @Test
  public void testStartStartStop()
  {
    final KafkaStream<String, String> kafkaStream = EasyMock.createStrictMock(KafkaStream.class);
    final ConsumerIterator<String, String> consumerIterator = EasyMock.createStrictMock(ConsumerIterator.class);
    final ConsumerConnector consumerConnector = EasyMock.createStrictMock(ConsumerConnector.class);
    EasyMock.expect(consumerConnector.createMessageStreamsByFilter(
        EasyMock.anyObject(TopicFilter.class),
        EasyMock.anyInt(),
        EasyMock.eq(
            DEFAULT_STRING_DECODER),
        EasyMock.eq(DEFAULT_STRING_DECODER)
    )).andReturn(ImmutableList.of(kafkaStream)).once();
    EasyMock.expect(kafkaStream.iterator()).andReturn(consumerIterator).once();
    EasyMock.expect(consumerIterator.hasNext()).andReturn(false).anyTimes();
    EasyMock.expect(cacheManager.getCacheMap(EasyMock.anyString()))
            .andReturn(new ConcurrentHashMap<String, String>())
            .once();
    EasyMock.expect(cacheManager.delete(EasyMock.anyString())).andReturn(true).once();
    consumerConnector.shutdown();
    EasyMock.expectLastCall().once();
    EasyMock.replay(cacheManager, kafkaStream, consumerConnector, consumerIterator);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("zookeeper.connect", "localhost")
    )
    {
      @Override
      ConsumerConnector buildConnector(Properties properties)
      {
        return consumerConnector;
      }
    };
    Assert.assertTrue(factory.start());
    Assert.assertFalse(factory.start());
    Assert.assertTrue(factory.close());
    EasyMock.verify(cacheManager, kafkaStream, consumerConnector, consumerIterator);
  }

  @Test
  public void testStartFailsOnMissingConnect()
  {
    expectedException.expectMessage("zookeeper.connect required property");
    EasyMock.replay(cacheManager);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.<String, String>of()
    );
    Assert.assertTrue(factory.start());
    Assert.assertTrue(factory.close());
    EasyMock.verify(cacheManager);
  }

  @Test
  public void testStartFailsOnGroupID()
  {
    expectedException.expectMessage(
        "Cannot set kafka property [group.id]. Property is randomly generated for you. Found");
    EasyMock.replay(cacheManager);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("group.id", "make me fail")
    );
    Assert.assertTrue(factory.start());
    Assert.assertTrue(factory.close());
    EasyMock.verify(cacheManager);
  }

  @Test
  public void testStartFailsOnAutoOffset()
  {
    expectedException.expectMessage(
        "Cannot set kafka property [auto.offset.reset]. Property will be forced to [smallest]. Found ");
    EasyMock.replay(cacheManager);
    final KafkaLookupExtractorFactory factory = new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        ImmutableMap.of("auto.offset.reset", "make me fail")
    );
    Assert.assertTrue(factory.start());
    Assert.assertTrue(factory.close());
    EasyMock.verify(cacheManager);
  }

  @Test
  public void testFailsGetNotStarted()
  {
    expectedException.expectMessage("Not started");
    new KafkaLookupExtractorFactory(
        cacheManager,
        TOPIC,
        DEFAULT_PROPERTIES
    ).get();
  }

  @Test
  public void testDefaultDecoder()
  {
    final String str = "some string";
    Assert.assertEquals(str, DEFAULT_STRING_DECODER.fromBytes(StringUtils.toUtf8(str)));
  }
}
