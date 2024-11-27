package de.kiaim.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to represent a project in the database.
 * Contains all configurations for the platform, for example, the data configuration or the anonymization configuration.
 *
 * @author Daniel Preciado-Marquez
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
	 * The original data.
	 */
	@OneToOne(optional = false, fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "original_data_id", referencedColumnName = "id")
	private OriginalDataEntity originalData = new OriginalDataEntity(this);

	/**
	 * Pipelines of the project.
	 */
	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@OrderBy("pipelineIndex")
	private final List<PipelineEntity> pipelines = new ArrayList<>();

	/**
	 * User that owns this configuration and the corresponding data set.
	 */
	@OneToOne(mappedBy = "project", optional = false, orphanRemoval = false, cascade = CascadeType.PERSIST)
	private UserEntity user;

	/**
	 * Adas a new pipeline to the project and links the entities.
	 * @param pipeline The pipeline to be added.
	 * @return The added pipeline for further usage.
	 */
	public PipelineEntity addPipeline(final PipelineEntity pipeline) {
		if (!pipelines.contains(pipeline)) {
			pipeline.setPipelineIndex(pipelines.size());
			pipelines.add(pipeline);
			pipeline.setProject(this);
		}

		return pipeline;
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
