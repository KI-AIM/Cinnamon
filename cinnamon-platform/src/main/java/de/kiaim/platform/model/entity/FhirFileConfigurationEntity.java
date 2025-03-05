package de.kiaim.platform.model.entity;


import de.kiaim.platform.model.file.FileType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * File configuration for FHIR resources.
 * Contains metadata that is required in order to read a file.
 */
@Entity
@DiscriminatorValue(value = FileType.Values.FHIR)
public class FhirFileConfigurationEntity extends FileConfigurationEntity {
	public FhirFileConfigurationEntity() {
		this.fileType = FileType.FHIR;
	}
}
