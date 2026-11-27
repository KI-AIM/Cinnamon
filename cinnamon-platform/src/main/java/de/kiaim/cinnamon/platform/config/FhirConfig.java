package de.kiaim.cinnamon.platform.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for FHIR processing.
 *
 * @author Daniel Preciado-Marquez
 */
@Configuration
public class FhirConfig {

	/**
	 * Shared FHIR context.
	 * Creating a context is expensive, while a created context is thread-safe and can be reused.
	 * Parsers created from the context are not thread-safe and should be created per use.
	 *
	 * @return The FHIR R4 context.
	 */
	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}
}
