package de.kiaim.platform.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a sigle step.
 */
@Getter @Setter
public class StepConfiguration {
	/**
	 * URL of the server.
	 */
	@NotBlank
	private String url;
}
