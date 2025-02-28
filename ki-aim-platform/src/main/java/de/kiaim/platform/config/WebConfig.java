package de.kiaim.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] corsAllowedOrigins;
	private final FormatBasedMessageConverter formatBasedMessageConverter;

	public WebConfig(@Value("${ki-aim.corsAllowedOrigins}") final String[] corsAllowedOrigins,
	                 final FormatBasedMessageConverter formatBasedMessageConverter) {
		this.corsAllowedOrigins = corsAllowedOrigins;
		this.formatBasedMessageConverter = formatBasedMessageConverter;
	}

	@Override
	public void extendMessageConverters(final List<HttpMessageConverter<?>> converters) {
		converters.add(formatBasedMessageConverter);
		WebMvcConfigurer.super.extendMessageConverters(converters);
	}

	@Override
	public void addCorsMappings(final CorsRegistry registry) {
		registry.addMapping("/**")
		        .allowedHeaders("*")
		        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
		        .allowedOrigins(corsAllowedOrigins);
	}
}
