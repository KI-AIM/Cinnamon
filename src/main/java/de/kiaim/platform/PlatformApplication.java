package de.kiaim.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
		basePackages = {
				"de.kiaim.platform.controller",
				"de.kiaim.platform.helper",
				"de.kiaim.platform.model",
				"de.kiaim.platform.processor",
				"de.kiaim.platform.reader",
				"de.kiaim.platform.service"
		}
)
@EnableAutoConfiguration
public class PlatformApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(PlatformApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(PlatformApplication.class, args);
	}

}
