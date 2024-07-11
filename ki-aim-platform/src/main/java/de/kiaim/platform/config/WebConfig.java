package de.kiaim.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	private final FormatBasedMessageConverter formatBasedMessageConverter;

	public WebConfig(final FormatBasedMessageConverter formatBasedMessageConverter) {
		this.formatBasedMessageConverter = formatBasedMessageConverter;
	}

	@Override
	public void extendMessageConverters(final List<HttpMessageConverter<?>> converters) {
		converters.add(formatBasedMessageConverter);
		WebMvcConfigurer.super.extendMessageConverters(converters);
	}
}
