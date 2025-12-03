package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Result of the file configuration estimation.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Result of the file configuration estimation.")
@Getter
public class FileConfigurationEstimation {

	/**
	 * The estimated file configuration.
	 */
	@Schema(description = "The estimated file configuration.")
	private final FileConfiguration estimation;

	/**
	 * List the resource types contained in the FHIR bundle,
	 * if the estimated file configuration has type {@link de.kiaim.cinnamon.platform.model.file.FileType#FHIR}
	 * Otherwise the value is null.
	 */
	@Schema(description = "Resource types of the FHIR bundle. Null if the file was not a FHIR bundle.",
	        example = "[Patient, Observation]")
	@Nullable
	private final Set<String> fhirResourceTypes;

	/**
	 * Creates a new instance from the given file configuration.
	 * Additional fields will be null.
	 *
	 * @param estimation The estimated file configuration
	 */
	public FileConfigurationEstimation(final FileConfiguration estimation) {
		this.estimation = estimation;
		this.fhirResourceTypes = null;
	}

	/**
	 * Creates a new instance from the given file configuration for FHIR bundles.
	 *
	 * @param estimation        The estimated file configuration
	 * @param fhirResourceTypes Resource types contained in the FHIR bundle.
	 */
	public FileConfigurationEstimation(final FileConfiguration estimation,
	                                   @Nullable final Set<String> fhirResourceTypes) {
		this.estimation = estimation;
		this.fhirResourceTypes = fhirResourceTypes;
	}

}
