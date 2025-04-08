package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Schema(description = "Request for fetching the available algorithms.")
@Data
public class AvailableAlgorithmsRequest {

	@Schema(description = "Name of the configuration.", example = "synthetization_configuration")
	@NotBlank
	private String configurationName;
}
