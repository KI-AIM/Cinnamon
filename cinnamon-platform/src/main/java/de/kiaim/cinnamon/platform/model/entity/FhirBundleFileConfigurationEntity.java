package de.kiaim.cinnamon.platform.model.entity;


import de.kiaim.cinnamon.model.configuration.data.file.FhirBundleFileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * File configuration for FHIR bundles.
 * Contains metadata required to read a file.
 */
@Entity
@DiscriminatorValue(value = FileType.Values.FHIR_BUNDLE)
@Getter @Setter
public class FhirBundleFileConfigurationEntity extends FileConfigurationEntity {

	/**
	 * The resource type to export from the bundle.
	 */
	@Nullable
	private String resourceType;

	/**
	 * Default constructor for Spring.
	 */
	public FhirBundleFileConfigurationEntity() {
		this.fileType = FileType.FHIR_BUNDLE;
	}

	/**
	 * Creates a new FHIR file configuration from the given DTO.
	 *
	 * @param configuration The configuration.
	 */
	public FhirBundleFileConfigurationEntity(final FhirBundleFileConfiguration configuration) {
		this.resourceType = configuration.getResourceType();
		this.fileType = FileType.FHIR_BUNDLE;
	}
}
