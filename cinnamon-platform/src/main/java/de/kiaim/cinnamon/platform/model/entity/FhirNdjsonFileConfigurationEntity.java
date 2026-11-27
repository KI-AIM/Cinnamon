package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity class for FHIR NDJSON file configuration.
 * The import of NDJSON files does not require any configuration by the user.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@DiscriminatorValue(value = FileType.Values.FHIR_NDJSON)
@Getter @Setter
public class FhirNdjsonFileConfigurationEntity extends FhirFileConfigurationEntity {

	/**
	 * Default constructor for Spring.
	 */
	public FhirNdjsonFileConfigurationEntity() {
		this.fileType = FileType.FHIR_NDJSON;
	}
}
