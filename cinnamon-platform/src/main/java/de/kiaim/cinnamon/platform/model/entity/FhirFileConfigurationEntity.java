package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.platform.converter.DataConfigurationAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Common file configuration for FHIR files.
 * <p>
 * Flattening a FHIR file requires parsing the whole file, which is expensive.
 * Therefore, the result of the flattening is derived once when the file configuration is stored
 * and reused for counting the columns, validating the column names and estimating the data configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public abstract class FhirFileConfigurationEntity extends FileConfigurationEntity {

	/**
	 * The data configuration derived from the FHIR datatypes of the flattened columns.
	 * Null if it has not been derived yet, e.g. because the resource type is not selected.
	 */
	@Convert(converter = DataConfigurationAttributeConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	@Nullable
	private DataConfiguration derivedDataConfiguration = null;
}
