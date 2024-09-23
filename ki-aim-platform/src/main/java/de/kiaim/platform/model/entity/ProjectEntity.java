package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

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
	 * Datasets of the project.
	 */
	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "step")
	private final Map<Step, DataSetEntity> dataSets = new HashMap<>();

	/**
	 * Other configurations for the platform.
	 * Stored as plain strings.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "configuration",
	                 joinColumns = @JoinColumn(name = "project_id", referencedColumnName = "id"))
	@MapKeyColumn(name = "configuration_name")
	@Column(name="configuration", length = Integer.MAX_VALUE)
	@Setter
	private Map<String, String> configurations = new HashMap<>();

	/**
	 * Executions of the project.
	 */
	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "step")
	private final Map<Step, ExecutionStepEntity> executions = new HashMap<>();

	/**
	 * User that owns this configuration and the corresponding data set.
	 */
	@OneToOne(mappedBy = "project", optional = false, orphanRemoval = false, cascade = CascadeType.PERSIST)
	private UserEntity user;

	public void putDataSet(final Step step, final DataSetEntity dataSet) {
		if (!dataSets.containsKey(step)) {
			dataSet.setProject(this);
			dataSet.setStep(step);
			dataSets.put(step, dataSet);
		}
	}

	public void removeDataSet(final Step step) {
		if (dataSets.containsKey(step)) {
			dataSets.get(step).setProject(null);
			dataSets.remove(step);
		}
	}

	public void putExecutionStep(final Step step, final ExecutionStepEntity executionStep) {
		if (!executions.containsKey(step)) {
			executionStep.setProject(this);
			executionStep.setStep(step);
			executions.put(step, executionStep);
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
}
