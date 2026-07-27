package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;

/**
 * Request body for uploading a file configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
@Setter
@NoArgsConstructor
public class UploadFileConfigurationRequest {

	@Parameter(description = "Configuration for the file.",
	           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	                              schema = @Schema(implementation = FileConfiguration.class)))
	@NotNull(message = "File Configuration must be present!")
	@Valid
	private FileConfiguration fileConfiguration;
}
