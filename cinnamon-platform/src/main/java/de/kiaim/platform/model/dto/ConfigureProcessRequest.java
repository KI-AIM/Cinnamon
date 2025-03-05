package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Request for configuring an external process.")
@Getter @Setter
public class ConfigureProcessRequest {

	@Schema(description = "Name of the job.", example = "SYNTHETIZATION")
	@NotBlank
	private String jobName;

	@Schema(description = "If the process should be skipped.")
	private boolean skip = false;
}
