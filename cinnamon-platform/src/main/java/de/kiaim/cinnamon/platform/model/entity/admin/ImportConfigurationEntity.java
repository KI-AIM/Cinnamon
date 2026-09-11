package de.kiaim.cinnamon.platform.model.entity.admin;

import de.kiaim.cinnamon.model.enumeration.AttributeMatchingStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleConfigOnlyStrategy;
import de.kiaim.cinnamon.model.enumeration.HandleDataOnlyStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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

}
