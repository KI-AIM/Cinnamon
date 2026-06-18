package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for uploading the data source configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class UploadDataSourceConfigurationRequest {

	@NotNull(message = "Data source configuration must be present.")
	@Valid
	private DataSourceConfiguration dataSourceConfiguration;

}
