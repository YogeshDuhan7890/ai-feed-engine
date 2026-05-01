package com.yogesh.config;

import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaErrorConfig {

	@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
	private String bootstrapServers;

	@Value("${app.kafka.retry.interval-ms:1000}")
	private long retryIntervalMs;

	@Value("${app.kafka.retry.max-attempts:3}")
	private long maxRetryAttempts;

	@Bean("dltProducerFactory")
	public ProducerFactory<String, Object> dltProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean("dltKafkaTemplate")
	public KafkaTemplate<String, Object> dltKafkaTemplate() {
		return new KafkaTemplate<>(dltProducerFactory());
	}

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
				(record, exception) -> {
					log.error(
							"Kafka record permanently failed after retries. topic={}, partition={}, offset={}, key={}, error={}",
							record.topic(), record.partition(), record.offset(), record.key(), exception.getMessage());
					return new TopicPartition(record.topic() + ".DLT", record.partition());
				});

		FixedBackOff backOff = new FixedBackOff(retryIntervalMs, maxRetryAttempts);
		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
		handler.addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class);
		handler.setRetryListeners((record, ex, deliveryAttempt) -> log.warn(
				"Kafka retry attempt {}/{} - topic={}, partition={}, offset={}, key={}, error={}",
				deliveryAttempt, maxRetryAttempts, record.topic(), record.partition(), record.offset(), record.key(),
				ex.getMessage()));

		return handler;
	}
}
