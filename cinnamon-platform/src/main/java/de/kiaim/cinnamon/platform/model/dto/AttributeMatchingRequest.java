package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.validation.UniqueColumnIndexConstraint;
import de.kiaim.cinnamon.model.validation.UniqueColumnNamesConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for replacing the attribute matching of the original data.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for replacing the attribute matching of the original data.")
@NoArgsConstructor
@Getter @Setter
public class AttributeMatchingRequest {

	/**
	 * Configurations of the attributes matched to the data.
	 * The index refers to the attribute in the data.
	 * Attributes of the data can be missing.
	 * The content of the configurations is not validated to allow storing drafts.
	 */
	@Schema(description = "Configurations of the attributes matched to the data. The index refers to the attribute in the data. Attributes of the data can be missing.")
	@NotNull
	@UniqueColumnIndexConstraint
	@UniqueColumnNamesConstraint
	private List<ColumnConfiguration> attributes = new ArrayList<>();

	/**
	 * Configurations of attributes that are not matched to the data and are ignored.
	 */
	@Schema(description = "Configurations of attributes that are not matched to the data and are ignored.")
	@NotNull
	private List<ColumnConfiguration> unmatchedAttributes = new ArrayList<>();
}
