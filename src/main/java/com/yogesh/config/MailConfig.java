package com.yogesh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.Properties;

@Configuration
public class MailConfig {

	@Value("${mail.zoho.host:smtp.zoho.in}")
	private String zohoHost;
	@Value("${mail.zoho.port:587}")
	private int zohoPort;
	@Value("${mail.zoho.username:}")
	private String zohoUsername;
	@Value("${mail.zoho.password:}")
	private String zohoPassword;

	
	@Bean("zohoMailSender")
	@Primary
	public JavaMailSender zohoMailSender() {
		JavaMailSenderImpl s = new JavaMailSenderImpl();
		s.setHost(zohoHost);
		s.setPort(zohoPort);
		if (zohoUsername != null && !zohoUsername.isBlank()) {
			s.setUsername(zohoUsername);
			s.setPassword(zohoPassword);
		}
		Properties p = s.getJavaMailProperties();
		p.put("mail.transport.protocol", "smtp");
		p.put("mail.smtp.auth", "true");
		p.put("mail.smtp.starttls.enable", "true");
		p.put("mail.smtp.starttls.required", "true");
		p.put("mail.smtp.connectiontimeout", "5000");
		p.put("mail.smtp.timeout", "5000");
		return s;
	}

	@Value("${mail.gmail.host:smtp.gmail.com}")
	private String gmailHost;
	@Value("${mail.gmail.port:587}")
	private int gmailPort;
	@Value("${mail.gmail.username:}")
	private String gmailUsername;
	@Value("${mail.gmail.password:}")
	private String gmailPassword;

	@Bean("gmailMailSender")
	public JavaMailSender gmailMailSender() {
		JavaMailSenderImpl s = new JavaMailSenderImpl();
		s.setHost(gmailHost);
		s.setPort(gmailPort);
		if (gmailUsername != null && !gmailUsername.isBlank()) {
			s.setUsername(gmailUsername);
			s.setPassword(gmailPassword);
		}
		Properties p = s.getJavaMailProperties();
		p.put("mail.transport.protocol", "smtp");
		p.put("mail.smtp.auth", "true");
		p.put("mail.smtp.starttls.enable", "true");
		p.put("mail.smtp.starttls.required", "true");
		p.put("mail.smtp.connectiontimeout", "5000");
		p.put("mail.smtp.timeout", "5000");
		return s;
	}
}