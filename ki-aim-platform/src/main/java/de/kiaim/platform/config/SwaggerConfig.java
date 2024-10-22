package de.kiaim.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI customOpenAPI(@Value("${ki-aim.version}") final String kiAimVersion) {
		return new OpenAPI()
				.components(new Components().addSecuritySchemes("Basic Authentication", new SecurityScheme().type(
						SecurityScheme.Type.HTTP).scheme("basic")))
				.addSecurityItem(new SecurityRequirement().addList("Basic Authentication"))
				.info(new Info().title("KI-AIM-Platform API")
				                .version(kiAimVersion)
				                .contact(new Contact().name("Github").url("https://github.com/KI-AIM/KI-AIM-Platform"))
				                .description("KI-AIM-Platform provides the data management for the KI-AIM project."));
	}

}
