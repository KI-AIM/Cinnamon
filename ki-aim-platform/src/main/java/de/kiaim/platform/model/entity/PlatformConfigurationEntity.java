package de.kiaim.platform.model.entity;

import de.kiaim.model.configuration.data.DataConfiguration;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class to represent the platform configuration in the database.
 * Contains all configurations for the platform, for example, the data configuration or the anonymization configuration.
 */
@Getter
@Entity
@NoArgsConstructor
public class PlatformConfigurationEntity {

	/**
	 * ID of the platform configuration.
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
	 * Other configurations for the platform.
	 * Stored as plain strings.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "configuration",
	                 joinColumns = @JoinColumn(name = "platform_configuration_id", referencedColumnName = "id"))
	@MapKeyColumn(name = "configuration_name")
	@Column(name="configuration")
	@Setter
	private Map<String, String> configurations = new HashMap<>();

	/**
	 * User that owns this configuration and the corresponding data set.
	 */
	@OneToOne(mappedBy = "platformConfiguration", optional = false, fetch = FetchType.LAZY, orphanRemoval = false,
	          cascade = CascadeType.PERSIST)
	private UserEntity user;

	/**
	 * List of transformation errors during the import.
	 */
	@OneToMany(mappedBy = "platformConfiguration", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	private final Set<DataTransformationErrorEntity> dataTransformationErrors = new HashSet<>();

	public void addDataRowTransformationError(final DataTransformationErrorEntity dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);

		if (dataTransformationError.getPlatformConfiguration() != this) {
			dataTransformationError.setPlatformConfiguration(this);
		}
	}
}
