package com.yogesh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

import com.yogesh.logincontroller.MaintenanceInterceptor;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	@Value("${upload.path}")
	private String uploadPath;

	@Value("${app.security.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}")
	private String allowedOrigins;
	
	private final MaintenanceInterceptor interceptor;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = uploadPath.endsWith("/") ? uploadPath : uploadPath + "/";
		String fileLocation = location.startsWith("file:") ? location : "file:" + location;

		// ── Uploaded videos/images — 1 hour cache ──
		registry.addResourceHandler("/uploads/**").addResourceLocations(fileLocation)
				.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());

		// ── Service worker — NO cache (must always be fresh) ──
		registry.addResourceHandler("/sw.js").addResourceLocations("classpath:/static/js/")
				.setCacheControl(CacheControl.noStore());

		// ── Images — 7 days cache ──
		registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/")
				.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());

		// ── CSS — 24 hours cache + must-revalidate ──
		registry.addResourceHandler("/css/**")
				.addResourceLocations("classpath:/static/css/", "classpath:/static/css/pages/")
				.setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic().mustRevalidate());

		// ── JS — 24 hours cache + must-revalidate ──
		registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/")
				.setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic().mustRevalidate());

		// ── Fonts — 1 year immutable cache ──
		registry.addResourceHandler("/fonts/**").addResourceLocations("classpath:/static/fonts/")
				.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

		// ── Manifest + favicon — 1 hour ──
		registry.addResourceHandler("/manifest.json", "/favicon.ico").addResourceLocations("classpath:/static/")
				.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
	}

	// ── CORS for API ──
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOriginPatterns(resolveAllowedOrigins())
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*").allowCredentials(true)
				.maxAge(3600);
	}

	private String[] resolveAllowedOrigins() {
		List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank())
				.toList();
		return origins.toArray(String[]::new);
	}

	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		// intentionally empty
	}
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}
