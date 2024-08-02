package de.kiaim.platform.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a sigle step.
 */
@Schema(description = "Configuration of a step.")
@Getter @Setter
public class StepConfiguration {

	@JsonIgnore
	@NotBlank
	private String callbackHost;

	/**
	 * URL of the server.
	 */
	@Schema(description = "URL of the corresponding server.", example = "https://my-anonymization-server.de")
	@NotBlank
	private String url;

}
