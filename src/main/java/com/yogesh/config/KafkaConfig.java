package com.yogesh.config;

import com.yogesh.event.EngagementEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

	@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
	private String bootstrapServers;

	@Value("${app.kafka.topics.video-processing:video-processing}")
	private String videoProcessingTopic;

	@Value("${app.kafka.topics.feed:feed-topic}")
	private String feedTopic;

	@Value("${app.kafka.topic-partitions:3}")
	private int topicPartitions;

	@Value("${app.kafka.topic-replicas:1}")
	private short topicReplicas;

	@Bean
	public org.apache.kafka.clients.admin.NewTopic videoProcessingTopic() {
		return TopicBuilder.name(videoProcessingTopic).partitions(topicPartitions).replicas(topicReplicas).build();
	}

	@Bean
	public org.apache.kafka.clients.admin.NewTopic videoProcessingDltTopic() {
		return TopicBuilder.name(videoProcessingTopic + ".DLT").partitions(topicPartitions).replicas(topicReplicas)
				.build();
	}

	@Bean
	public org.apache.kafka.clients.admin.NewTopic feedTopic() {
		return TopicBuilder.name(feedTopic).partitions(topicPartitions).replicas(topicReplicas).build();
	}

	@Bean
	public org.apache.kafka.clients.admin.NewTopic feedDltTopic() {
		return TopicBuilder.name(feedTopic + ".DLT").partitions(topicPartitions).replicas(topicReplicas).build();
	}

	@Bean
	public ConsumerFactory<String, Long> consumerFactory() {
		Map<String, Object> config = baseConsumerConfig("video-group");
		config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, LongDeserializer.class);
		return new DefaultKafkaConsumerFactory<>(config);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Long> kafkaListenerContainerFactory(
			DefaultErrorHandler kafkaErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<String, Long> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory());
		factory.setCommonErrorHandler(kafkaErrorHandler);
		return factory;
	}

	@Bean
	public ProducerFactory<String, Long> producerFactory() {
		Map<String, Object> config = baseProducerConfig();
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, Long> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}

	@Bean
	public ProducerFactory<String, EngagementEvent> engagementProducerFactory() {
		Map<String, Object> config = baseProducerConfig();
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, EngagementEvent> engagementKafkaTemplate() {
		return new KafkaTemplate<>(engagementProducerFactory());
	}

	@Bean
	public ConsumerFactory<String, EngagementEvent> engagementConsumerFactory() {
		Map<String, Object> config = baseConsumerConfig("feed-group");
		config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
		config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.yogesh.event");
		config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EngagementEvent.class.getName());
		return new DefaultKafkaConsumerFactory<>(config);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, EngagementEvent> engagementKafkaListenerContainerFactory(
			DefaultErrorHandler kafkaErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<String, EngagementEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(engagementConsumerFactory());
		factory.setCommonErrorHandler(kafkaErrorHandler);
		return factory;
	}

	private Map<String, Object> baseConsumerConfig(String groupId) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
		return config;
	}

	private Map<String, Object> baseProducerConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
		config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
		config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
		return config;
	}
}
