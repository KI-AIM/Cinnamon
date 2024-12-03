package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.file.FileConfiguration;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class EstimateDataConfigurationRequest {

	@Parameter(description = "File containing the data.",
	           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
	@NotNull(message = "Data must be present!")
	private MultipartFile file;

	@Parameter(description = "Configuration for the file.",
	           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	                              schema = @Schema(implementation = FileConfiguration.class)))
	@NotNull(message = "File Configuration must be present!")
	@Valid
	private FileConfiguration fileConfiguration;
}
