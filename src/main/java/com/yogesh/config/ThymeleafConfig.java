package com.yogesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class ThymeleafConfig {

	@Bean
	public SpringResourceTemplateResolver structureTemplateResolver() {
		SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
		resolver.setPrefix("classpath:/structure/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCheckExistence(true);
		resolver.setCacheable(false);
		resolver.setOrder(1);
		return resolver;
	}

	@Bean
	public SpringResourceTemplateResolver componentsTemplateResolver() {
		SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
		resolver.setPrefix("classpath:/structure/components/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCheckExistence(true);
		resolver.setCacheable(false);
		resolver.setOrder(2);
		return resolver;
	}

	@Bean
	public SpringResourceTemplateResolver layoutTemplateResolver() {
		SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
		resolver.setPrefix("classpath:/templates/layout/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCheckExistence(true);
		resolver.setCacheable(false);
		resolver.setOrder(3);
		return resolver;
	}

	@Bean
	@Primary
	public SpringTemplateEngine templateEngine() {
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setEnableSpringELCompiler(true);
		engine.addTemplateResolver(structureTemplateResolver());
		engine.addTemplateResolver(componentsTemplateResolver());
		engine.addTemplateResolver(layoutTemplateResolver());
		return engine;
	}

	@Bean
	public ThymeleafViewResolver viewResolver() {
		ThymeleafViewResolver resolver = new ThymeleafViewResolver();
		resolver.setTemplateEngine(templateEngine());
		resolver.setCharacterEncoding("UTF-8");
		resolver.setOrder(Integer.MIN_VALUE); // FIX: Spring Boot default resolvers se pehle run hoga
		resolver.setViewNames(new String[] { "*" }); // FIX: saare views handle karega
		return resolver;
	}
}