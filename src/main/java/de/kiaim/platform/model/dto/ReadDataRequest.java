package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.validation.MatchingNumberColumnsConstraint;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@MatchingNumberColumnsConstraint
@Getter @Setter
public class ReadDataRequest {

	@Parameter(description = "File containing the data.",
	           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
	@NotNull(message = "Data must be present!")
	private MultipartFile file;

	@Parameter(description = "Configuration for the file.",
	           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	                              schema = @Schema(implementation = FileConfiguration.class)))
	@NotNull(message = "File Configuration must be present!")
	private FileConfiguration fileConfiguration;

	@Parameter(description = "Metadata describing the format of the data.",
	           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	                              schema = @Schema(implementation = DataConfiguration.class)))
	@NotNull(message = "Configuration must be present!")
	@Valid
	private DataConfiguration configuration;
}
