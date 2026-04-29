package de.kiaim.cinnamon.model.configuration.data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for retrieving the data from a server.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration for a data source server")
@NoArgsConstructor
@Getter @Setter
public class DataSourceServerConfiguration {

	/**
	 * URL of the server where the file is located.
	 */
	@Schema(description = "URL of the server where the file is located")
	@NotBlank
	private String url;
}
