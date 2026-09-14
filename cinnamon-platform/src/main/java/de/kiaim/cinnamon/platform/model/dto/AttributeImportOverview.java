package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.ImportConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Overview of the attribute matching between the uploaded configuration and the data.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Overview of the attribute matching between the uploaded configuration and the data.")
@AllArgsConstructor
@Getter
public class AttributeImportOverview {

	/**
	 * The import configuration stored in the database.
	 * The attributes only contain the attributes matched to the data.
	 */
	@Schema(description = "The import configuration. The attributes only contain the attributes matched to the data.")
	private final ImportConfigurationDTO importConfiguration;

	/**
	 * Attribute configurations that are only present in the configuration and are ignored.
	 */
	@Schema(description = "Attribute configurations that are only present in the configuration and are ignored.")
	private final List<ColumnConfiguration> configOnlyAttributes;

	/**
	 * Attributes that are only present in the data and are missing a configuration.
	 */
	@Schema(description = "Attributes that are only present in the data and are missing a configuration.")
	private final List<MissingAttribute> dataOnlyAttributes;
}
