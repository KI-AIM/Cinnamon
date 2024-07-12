package de.kiaim.platform.model.entity;

import de.kiaim.model.configuration.data.DataConfiguration;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class to represent a project in the database.
 * Contains all configurations for the platform, for example, the data configuration or the anonymization configuration.
 */
@Getter
@Entity
@NoArgsConstructor
public class ProjectEntity {

	/**
	 * ID of the project.
	 * Is also used to identify the table for the data set.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Current status of the project.
	 */
	@OneToOne(optional = false, fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "status_id", referencedColumnName = "id")
	private StatusEntity status = new StatusEntity(this);

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
	                 joinColumns = @JoinColumn(name = "project_id", referencedColumnName = "id"))
	@MapKeyColumn(name = "configuration_name")
	@Column(name="configuration")
	@Setter
	private Map<String, String> configurations = new HashMap<>();

	/**
	 * User that owns this configuration and the corresponding data set.
	 */
	@OneToOne(mappedBy = "project", optional = false, fetch = FetchType.LAZY, orphanRemoval = false,
	          cascade = CascadeType.PERSIST)
	private UserEntity user;

	/**
	 * List of transformation errors during the import.
	 */
	@OneToMany(mappedBy = "project", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	private final Set<DataTransformationErrorEntity> dataTransformationErrors = new HashSet<>();


	/**
	 * Links the given external process with this project.
	 * @param newExternalProcess The process to link.
	 */
	public void setExternalProcess(@Nullable final ExternalProcessEntity newExternalProcess) {
		final ExternalProcessEntity oldExternalProcess = this.externalProcess;
		this.externalProcess = newExternalProcess;
		if (oldExternalProcess != null && oldExternalProcess.getProject() == this) {
			oldExternalProcess.setProject(null);
		}
		if (newExternalProcess != null && newExternalProcess.getProject() != this) {
			newExternalProcess.setProject(this);
		}
	}

	/**
	 * Links the given user with this project.
	 * @param newUser The user to link.
	 */
	public void setUser(@Nullable final UserEntity newUser) {
		final UserEntity oldUser = this.user;
		this.user = newUser;
		if (oldUser != null && oldUser.getProject() == this) {
			oldUser.setProject(null);
		}
		if (newUser != null && newUser.getProject() != this) {
			newUser.setProject(this);
		}
	}

	public void addDataRowTransformationError(final DataTransformationErrorEntity dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);

		if (dataTransformationError.getProject() != this) {
			dataTransformationError.setProject(this);
		}
	}
}
