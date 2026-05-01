package com.yogesh.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonLogLayout extends LayoutBase<ILoggingEvent> {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public String doLayout(ILoggingEvent event) {
		Map<String, Object> json = new LinkedHashMap<>();
		Map<String, String> mdc = event.getMDCPropertyMap();

		json.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
		json.put("level", event.getLevel().toString());
		json.put("logger", event.getLoggerName());
		json.put("thread", event.getThreadName());
		json.put("message", event.getFormattedMessage());
		putIfPresent(json, "requestId", mdc.get("requestId"));
		putIfPresent(json, "traceId", mdc.get("traceId"));
		putIfPresent(json, "method", mdc.get("method"));
		putIfPresent(json, "path", mdc.get("path"));
		putIfPresent(json, "status", mdc.get("status"));
		putIfPresent(json, "durationMs", mdc.get("durationMs"));
		putIfPresent(json, "clientIp", mdc.get("clientIp"));

		IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable != null) {
			json.put("exception", throwable.getClassName());
			json.put("exceptionMessage", throwable.getMessage());
			json.put("stackTrace", ThrowableProxyUtil.asString(throwable));
		}

		try {
			return OBJECT_MAPPER.writeValueAsString(json) + System.lineSeparator();
		} catch (JsonProcessingException e) {
			return "{\"level\":\"ERROR\",\"message\":\"Failed to serialize log event\"}" + System.lineSeparator();
		}
	}

	private void putIfPresent(Map<String, Object> json, String key, String value) {
		if (value != null && !value.isBlank()) {
			json.put(key, value);
		}
	}
}
