package de.kiaim.cinnamon.platform.model.entity;


import de.kiaim.cinnamon.platform.model.file.FhirFileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * File configuration for FHIR bundles.
 * Contains metadata required to read a file.
 */
@Entity
@DiscriminatorValue(value = FileType.Values.FHIR)
@Getter @Setter
public class FhirFileConfigurationEntity extends FileConfigurationEntity {

	/**
	 * The resource type to export from the bundle.
	 */
	private String resourceType;

	/**
	 * Default constructor for Spring.
	 */
	public FhirFileConfigurationEntity() {
		this.fileType = FileType.FHIR;
	}

	/**
	 * Creates a new FHIR file configuration from the given DTO.
	 *
	 * @param configuration The configuration.
	 */
	public FhirFileConfigurationEntity(final FhirFileConfiguration configuration) {
		this.resourceType = configuration.getResourceType();
		this.fileType = FileType.FHIR;
	}
}
