package de.kiaim.cinnamon.model.configuration.data;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.model.validation.DataSourceConfigured;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * DTO for the data source configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Specifies the data source for the file containing the original data.")
@JsonInclude(JsonInclude.Include.NON_NULL)
@DataSourceConfigured
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfiguration implements ConfigurationDTO {

	/**
	 * Type of the data source.
	 */
	@Schema(description = "Source of the file.")
	@NotNull(message = "Data source type must be present.")
	private DataSourceType dataSourceType;

	/**
	 * Configuration for the server where the file is located.
	 */
	@Schema(description = "Configuration for the server where the file is located.")
	@Nullable
	private DataSourceServerConfiguration server;

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY;
	}
}
