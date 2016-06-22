/*
 * Copyright 2016 David Murphy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.demo.kafka.ejb;


import static javax.ejb.TransactionAttributeType.NOT_SUPPORTED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.redhat.demo.kafka.KafkaClient;

/**
 * Simple, transaction-free wrapper around Kafka Consumer/Producer
 *
 */
@Stateless
@TransactionAttribute(NOT_SUPPORTED)
public class KafkaClientBean implements KafkaClient {

	
	/* Configuration */
	private static final Long TIMEOUT = Long.parseLong(System.getProperty("org.apache.kafka.poll.timeout", "100"));
	private static final List<String> TOPICS = Arrays.asList(System.getProperty("org.apache.kafka.topics", "kafka.ejb.client"));
	private static final String BOOTSTRAP_SERVERS = System.getProperty("org.apache.kafka.boostrap.servers","localhost:9092");
	private static final String ACKS = System.getProperty("org.apache.kafka.acks","all");
	private static final Integer RETRIES = Integer.parseInt(System.getProperty("org.apache.kafka.retries", "0"));
	private static final Integer BATCH_SIZE = Integer.parseInt(System.getProperty("org.apache.kafka.batch.size", "16384"));
	private static final Integer LINGER_MS = Integer.parseInt(System.getProperty("org.apache.kafka.linger.ms", "1"));
	private static final Integer BUFFER_MEMORY = Integer.parseInt(System.getProperty("org.apache.kafka.buffer.memory", "33554432"));
	
	private static final String GROUP_ID = System.getProperty("org.apache.kafka.group.id", "kafka.ejb.client");
	private static final String ENABLE_AUTO_COMMIT = System.getProperty("org.apache.kafka.enable.auto.commit", "true");
	private static final String AUTO_COMMIT_INTERVAL_MS = System.getProperty("org.apache.kafka.auto.commit.interval.ms", "1000");
	private static final String SESSION_TIMEOUT_MS = System.getProperty("org.apache.kafka.session.timeout.ms", "30000");
	 
	
	Properties producerProperties;
	Properties consumerProperties;

	/* Producer resources */
	KafkaProducer<String, String> producer;

	/* Consumer resources */
	@Resource
	SessionContext context;
	List<Future<Void>> handles = new ArrayList<>();

	@PostConstruct
	public void init() {
		
		producerProperties = new Properties();
		producerProperties.put("bootstrap.servers", BOOTSTRAP_SERVERS);
		producerProperties.put("acks", ACKS);
		producerProperties.put("retries", RETRIES);
		producerProperties.put("batch.size", BATCH_SIZE);
		producerProperties.put("linger.ms", LINGER_MS);
		producerProperties.put("buffer.memory", BUFFER_MEMORY);
		producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		
		producer = new KafkaProducer<>(producerProperties);

		consumerProperties = new Properties();
		consumerProperties.put("bootstrap.servers", BOOTSTRAP_SERVERS);
		consumerProperties.put("group.id", GROUP_ID);
		consumerProperties.put("enable.auto.commit", ENABLE_AUTO_COMMIT);
		consumerProperties.put("auto.commit.interval.ms", AUTO_COMMIT_INTERVAL_MS);
		consumerProperties.put("session.timeout.ms", SESSION_TIMEOUT_MS);
		consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
	}

	@PreDestroy
	public void stop() {
		handles.forEach(h -> h.cancel(true));
		producer.close();
	}

	public void addHandle(Future<Void> handle) {
		handles.add(handle);
	}

	/**
	 * Create a consumer and start it polling. Since consumers aren't thread
	 * safe, we can't share this. Use @Asynchronous to manage the work in a
	 * separate thread. Call .cancel(true) on the returned Future to stop the
	 * consumer.
	 * 
	 * @param recordCallback
	 *            BiConsumer<String, String> used to process each record.
	 * @return Future<Void> handle to allow clean shutdown of consumer.
	 */
	@Asynchronous
	public Future<Void> startConsumer(BiConsumer<String, String> recordCallback) {
		KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
				consumerProperties);
		consumer.subscribe(TOPICS);

		while (!context.wasCancelCalled()) {
			consumer.poll(TIMEOUT).forEach(
					r -> recordCallback.accept(r.key(), r.value()));
		}

		if (consumer != null) {
			consumer.close();
		}

		return new AsyncResult<Void>(null);

	}

	/**
	 * Send a record to a topic. This is a dumb pipe right now. 
	 * TODO: Make this a little more intelligent
	 * 
	 * @param topic
	 *            String topic to send record to
	 * @param key
	 *            String record key
	 * @param value
	 *            String record value
	 */
	public void send(String topic, String key, String value) {
		producer.send(new ProducerRecord<>(topic, key, value));
	}
}
