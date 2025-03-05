package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.configuration.ExternalConfiguration;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.*;

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
	 * Seed for the random operations.
	 */
	@Column(nullable = false)
	@Getter(AccessLevel.NONE)
	private long seed;

	/**
	 * Number of times the random has been called.
	 */
	@Column(nullable = false)
	@Getter(AccessLevel.NONE)
	private int randomCalls = 0;

	/**
	 * Current status of the project.
	 */
	@OneToOne(optional = false, fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "status_id", referencedColumnName = "id")
	private StatusEntity status = new StatusEntity(this);

	/**
	 * User configuration of the project.
	 */
	@OneToOne(optional = false, fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "project_configuration_id", referencedColumnName = "id")
	private ProjectConfigurationEntity projectConfiguration = new ProjectConfigurationEntity(this);

	/**
	 * The original data.
	 */
	@OneToOne(optional = false, fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "original_data_id", referencedColumnName = "id", nullable = false)
	private final OriginalDataEntity originalData = new OriginalDataEntity(this);

	/**
	 * List of configurations for each configuration definition.
	 */
	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@Getter
	private final Set<ConfigurationListEntity> configurations = new HashSet<>();

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
	 * Creates a new project with the given seed.
	 *
	 * @param seed The seed.
	 */
	public ProjectEntity(final long seed) {
		this.seed = seed;
	}

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

	/**
	 * Returns the configuration list for the given definition.
	 * The list will be created if it doesn't exist yet.
	 *
	 * @param configuration The definition.
	 * @return The list.
	 */
	public ConfigurationListEntity addConfigurationList(final ExternalConfiguration configuration) {
		ConfigurationListEntity configurationList = getConfigurationList(configuration);
		if (configurationList == null) {
			configurationList = new ConfigurationListEntity(configuration);
			configurations.add(configurationList);
		}
		return configurationList;
	}

	/**
	 * Returns the configuration list for the given definition.
	 * If no list exists, return null.
	 *
	 * @param configuration The definition.
	 * @return The list.
	 */
	@Nullable
	public ConfigurationListEntity getConfigurationList(final ExternalConfiguration configuration) {
		for (ConfigurationListEntity configurationList : configurations) {
			if (configurationList.getConfiguration().equals(configuration)) {
				return configurationList;
			}
		}
		return null;
	}

	/**
	 * Creates a random double using the seed of the project.
	 *
	 * @param origin The min value (inclusive).
	 * @param bound  The max value (exclusive).
	 * @return The random double.
	 */
	public double randomDouble(final double origin, final double bound) {
		Random random = prepareRandom();
		randomCalls++;
		return random.nextDouble(origin, bound);
	}

	private Random prepareRandom() {
		Random random = new Random(seed);
		for (int i = 0; i < randomCalls; i++) {
			random.nextInt();
		}
		return random;
	}
}
