package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * Class to represent a data configuration in the database.
 */
@Getter
@Entity
@NoArgsConstructor
public class DataConfigurationEntity {

	/**
	 * ID of the configuration.
	 * Is also used to identify the table for the data set.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * The data configuration
	 */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Setter
	private DataConfiguration dataConfiguration;

	/**
	 * User that owns this configuration and the corresponding data set.
	 */
	@OneToOne(mappedBy = "dataConfiguration", optional = false, fetch = FetchType.LAZY, orphanRemoval = false,
	          cascade = CascadeType.PERSIST)
	private UserEntity user;

	/**
	 * List of transformation errors during the import.
	 */
	@OneToMany(mappedBy = "dataConfiguration", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	private final Set<DataTransformationErrorEntity> dataTransformationErrors = new HashSet<>();

	public void addDataRowTransformationError(final DataTransformationErrorEntity dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);

		if (dataTransformationError.getDataConfiguration() != this) {
			dataTransformationError.setDataConfiguration(this);
		}
	}
}
