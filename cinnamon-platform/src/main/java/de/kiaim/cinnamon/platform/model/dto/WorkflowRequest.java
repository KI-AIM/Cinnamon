package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request parameter for starting a workflow.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request parameter for starting a workflow.")
@NoArgsConstructor
@Getter @Setter
public class WorkflowRequest {

	/**
	 * File containing the data to be anonymized.
	 * Allowed to be null if the data is fetched from a server.
	 */
	@Schema(description = "File containing the data to be anonymized.", requiredMode = Schema.RequiredMode.REQUIRED)
	private MultipartFile data;

	/**
	 * Configuration file containing the anonymization rules.
	 */
	@Schema(description = "File containing the configuration for the workflow.",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "Configuration file is required!")
	private MultipartFile configuration;

}
