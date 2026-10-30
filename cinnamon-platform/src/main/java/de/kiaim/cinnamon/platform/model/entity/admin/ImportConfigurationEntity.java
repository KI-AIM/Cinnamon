package de.kiaim.cinnamon.platform.model.entity.admin;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.enumeration.AttributeMatchingStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleConfigOnlyStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleDataOnlyStrategy;
import de.kiaim.cinnamon.platform.converter.DataConfigurationAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Entity representing the configuration for the import process.
 * Specifies how the attribute configuration is applied to the imported dataset and the handling of unmatched attributes.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public class ImportConfigurationEntity {

	/**
	 * Primary key.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private @Nullable Long id;

	/**
	 * Defines how attributes are matched between the configuration and the imported dataset.
	 * See {@link AttributeMatchingStrategy} for available matching strategies and detailed info.
	 */
	@Column(nullable = false)
	private AttributeMatchingStrategy attributeMatching = AttributeMatchingStrategy.NAME;

	/**
	 * Defines how to handle attributes that are present in the configuration but not in the imported dataset.
	 * See {@link HandleConfigOnlyStrategy} for available strategies and detailed info.
	 */
	@Column(nullable = false)
	private HandleConfigOnlyStrategy handleConfigOnly = HandleConfigOnlyStrategy.IGNORE;

	/**
	 * Defines how to handle attributes that are present in the imported dataset but not in the configuration.
	 * See {@link HandleDataOnlyStrategy} for available strategies and detailed info.
	 */
	@Column(nullable = false)
	private HandleDataOnlyStrategy handleDataOnly = HandleDataOnlyStrategy.ERROR;

	/**
	 * The data configuration containing the attributes matched to the data.
	 * Shared by all datasets of the project.
	 * Null if no attributes have been configured yet.
	 */
	@Convert(converter = DataConfigurationAttributeConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	@Nullable
	private DataConfiguration dataConfiguration = null;

	/**
	 * Attribute configurations that could not be matched to an attribute in the data (config-only attributes).
	 * Kept separately from the data configuration, so the data configuration always matches the data.
	 * Used for displaying the attributes and for re-matching them if the file or the matching strategy changes.
	 * Null if no attribute matching has been performed yet.
	 */
	@Convert(converter = DataConfigurationAttributeConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	@Nullable
	private DataConfiguration unmatchedAttributes = null;
}
