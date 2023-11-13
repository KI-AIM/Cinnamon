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
				"de.kiaim.controller",
				"de.kiaim.helper",
				"de.kiaim.model",
				"de.kiaim.processor",
				"de.kiaim.reader",
				"de.kiaim.service"
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
