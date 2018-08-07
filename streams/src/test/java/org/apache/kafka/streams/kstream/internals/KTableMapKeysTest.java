/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.kstream.internals;


import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.apache.kafka.test.MockProcessorSupplier;
import org.apache.kafka.test.StreamsTestUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class KTableMapKeysTest {

    private final ConsumerRecordFactory<Integer, String> recordFactory = new ConsumerRecordFactory<>(new IntegerSerializer(), new StringSerializer());
    private final Properties props = StreamsTestUtils.topologyTestConfig(Serdes.Integer(), Serdes.String());

    @Test
    public void testMapKeysConvertingToStream() {
        final StreamsBuilder builder = new StreamsBuilder();

        final String topic1 = "topic_map_keys";

        final KTable<Integer, String> table1 = builder.table(topic1, Consumed.with(Serdes.Integer(), Serdes.String()));

        final Map<Integer, String> keyMap = new HashMap<>();
        keyMap.put(1, "ONE");
        keyMap.put(2, "TWO");
        keyMap.put(3, "THREE");

        final KeyValueMapper<Integer, String, String> keyMapper = new KeyValueMapper<Integer, String, String>() {
            @Override
            public  String apply(final Integer key, final String value) {
                return keyMap.get(key);
            }
        };

        final KStream<String, String> convertedStream = table1.toStream(keyMapper);

        final String[] expected = new String[]{"ONE:V_ONE", "TWO:V_TWO", "THREE:V_THREE"};
        final int[] originalKeys = new int[]{1, 2, 3};
        final String[] values = new String[]{"V_ONE", "V_TWO", "V_THREE"};

        final MockProcessorSupplier<String, String> supplier = new MockProcessorSupplier<>();

        convertedStream.process(supplier);

        try (final TopologyTestDriver driver = new TopologyTestDriver(builder.build(), props)) {
            for (int i = 0; i < originalKeys.length; i++) {
                driver.pipeInput(recordFactory.create(topic1, originalKeys[i], values[i]));
            }
        }

        assertEquals(3, supplier.theCapturedProcessor().processed.size());

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], supplier.theCapturedProcessor().processed.get(i));
        }
    }
}