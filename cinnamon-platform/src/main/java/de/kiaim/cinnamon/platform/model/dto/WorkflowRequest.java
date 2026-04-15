package de.kiaim.cinnamon.platform.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@NoArgsConstructor
@Getter @Setter
public class WorkflowRequest {

	/**
	 * File containing the data to be anonymized.
	 */
	@NotNull(message = "Data must be present!")
	private MultipartFile data;

	/**
	 * Configuration file containing the anonymization rules.
	 */
	@NotNull(message = "Configuration must be present!")
	private MultipartFile configuration;

}
