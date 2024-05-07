package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.file.FileConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter @Setter
public class ReadDataRequest {

	@Schema(description = "File containing the data.")
	@NotNull(message = "Data must be present!")
	private MultipartFile file;

	@Schema(implementation = FileConfiguration.class)
	@NotNull(message = "File Configuration must be present!")
	private FileConfiguration fileConfiguration;

	@Schema(implementation = DataConfiguration.class)
	@NotNull(message = "Configuration must be present!")
	@Valid
	private DataConfiguration configuration;
}
