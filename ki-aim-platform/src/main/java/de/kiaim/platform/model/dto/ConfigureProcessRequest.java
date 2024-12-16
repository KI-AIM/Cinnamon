package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.validation.ValidConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Schema(description = "Request for configuring an external process.")
@ValidConfiguration
@Getter @Setter
public class ConfigureProcessRequest {

	@Schema(description = "Name of the step.", example = "SYNTHETIZATION")
	@NotBlank
	private String stepName;

	@Schema(description = "URI to start the algorithm.", example = "/start_synthetization_process/ctgan")
	@Nullable
	private String url;

	@Schema(description = "Process specific configuration.")
	@Nullable
	private String configuration;

	@Schema(description = "If the process should be skipped.")
	private boolean skip = false;
}
