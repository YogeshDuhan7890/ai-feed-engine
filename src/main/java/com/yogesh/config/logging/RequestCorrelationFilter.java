package com.yogesh.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	private static final String REQUEST_ID_HEADER = "X-Request-ID";
	private static final String TRACE_ID_HEADER = "X-Trace-ID";
	private static final String B3_TRACE_ID_HEADER = "X-B3-TraceId";
	private static final String TRACEPARENT_HEADER = "traceparent";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startedAt = System.currentTimeMillis();
		String requestId = firstNonBlank(request.getHeader(REQUEST_ID_HEADER), UUID.randomUUID().toString());
		String traceId = resolveTraceId(request, requestId);

		MDC.put("requestId", requestId);
		MDC.put("traceId", traceId);
		MDC.put("method", request.getMethod());
		MDC.put("path", request.getRequestURI());
		MDC.put("clientIp", clientIp(request));
		response.setHeader(REQUEST_ID_HEADER, requestId);
		response.setHeader(TRACE_ID_HEADER, traceId);

		boolean failed = false;
		try {
			filterChain.doFilter(request, response);
		} catch (ServletException | IOException | RuntimeException e) {
			failed = true;
			MDC.put("status", "500");
			MDC.put("durationMs", String.valueOf(System.currentTimeMillis() - startedAt));
			log.error("HTTP request failed", e);
			throw e;
		} finally {
			MDC.put("status", String.valueOf(response.getStatus()));
			MDC.put("durationMs", String.valueOf(System.currentTimeMillis() - startedAt));
			if (!failed) {
				logRequest(response.getStatus());
			}
			MDC.clear();
		}
	}

	private void logRequest(int status) {
		if (status >= 500) {
			log.error("HTTP request completed");
		} else if (status >= 400) {
			log.warn("HTTP request completed");
		} else {
			log.info("HTTP request completed");
		}
	}

	private String resolveTraceId(HttpServletRequest request, String requestId) {
		String traceparent = request.getHeader(TRACEPARENT_HEADER);
		if (traceparent != null) {
			String[] parts = traceparent.split("-");
			if (parts.length >= 2 && parts[1].matches("[0-9a-fA-F]{32}")) {
				return parts[1].toLowerCase();
			}
		}
		return firstNonBlank(
				request.getHeader(TRACE_ID_HEADER),
				request.getHeader(B3_TRACE_ID_HEADER),
				requestId);
	}

	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
