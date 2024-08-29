package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Request for starting an external process.")
@Getter @Setter
public class StartProcessRequest {

	@Schema(description = "Name of the step.", example = "SYNTHETIZATION")
	@NotBlank
	private String stepName;

	@Schema(description = "URI to start the algorithm.", example = "/start_synthetization_process/ctgan")
	@NotBlank
	private String url;

	@Schema(description = "Process specific configuration.")
	@NotBlank
	private String configuration;
}
