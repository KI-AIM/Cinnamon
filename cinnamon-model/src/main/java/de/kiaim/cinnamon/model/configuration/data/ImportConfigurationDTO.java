package de.kiaim.cinnamon.model.configuration.data;

import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.enumeration.AttributeMatchingStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleConfigOnlyStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleDataOnlyStrategy;
import de.kiaim.cinnamon.model.validation.UniqueColumnIndexConstraint;
import de.kiaim.cinnamon.model.validation.UniqueColumnNamesConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Class defining the configuration for the import process.
 * Specifies how the attribute configuration is applied to the imported dataset and the handling of unmatched attributes.
 * TODO Validate column configuration based on the attribute matching strategy (e.g., if matching by index, ensure that all indices are unique and present).
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Class defining the configuration for the import process.")
@Getter @Setter
public class ImportConfigurationDTO implements ConfigurationDTO {

	/**
	 * Defines how attributes are matched between the configuration and the imported dataset.
	 * See {@link AttributeMatchingStrategy} for available matching strategies and detailed info.
	 */
	@Schema(description = "Defines how attributes are matched between the configuration and the imported dataset.")
	@NotNull
	private AttributeMatchingStrategy attributeMatching = AttributeMatchingStrategy.NAME;

	/**
	 * Defines how to handle attributes that are present in the configuration but not in the imported dataset.
	 * See {@link HandleConfigOnlyStrategy} for available strategies and detailed info.
	 */
	@Schema(description = "Defines how to handle attributes that are present in the configuration but not in the imported dataset.")
	@NotNull
	private HandleConfigOnlyStrategy handleConfigOnly = HandleConfigOnlyStrategy.IGNORE;

	/**
	 * Defines how to handle attributes that are present in the imported dataset but not in the configuration.
	 * See {@link HandleDataOnlyStrategy} for available strategies and detailed info.
	 */
	@Schema(description = "Defines how to handle attributes that are present in the imported dataset but not in the configuration.")
	@NotNull
	private HandleDataOnlyStrategy handleDataOnly = HandleDataOnlyStrategy.ERROR;

	/**
	 * List of attribute configurations defining the expected attributes in the imported dataset.
	 */
	@Schema(description = "List of attribute configurations defining the expected attributes in the imported dataset.")
	@NotNull
	@UniqueColumnIndexConstraint
	@UniqueColumnNamesConstraint
	private List<@Valid ColumnConfiguration> attributes;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getKey() {
		return ConfigurationFile.IMPORT_CONFIGURATION_KEY;
	}
}
