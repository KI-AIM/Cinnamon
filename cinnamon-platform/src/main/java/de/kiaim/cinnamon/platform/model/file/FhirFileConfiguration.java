package de.kiaim.cinnamon.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configurations specific for FHIR bundles.
 *
 * @author Daniel Preciado-Marquez
 */
@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
@Schema(description = "Configurations specific for FHIR bundles.")
public class FhirFileConfiguration {

	/**
	 * The resource type to export from the bundle.
	 */
	@Schema(description = "The resource type to export from the bundle.", example = "Patient")
	@NotNull(message = "Resource type must be present")
	private String resourceType;
}
