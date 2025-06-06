package de.kiaim.cinnamon.platform.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * Entity containing all configurations for a project.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @Setter
public class ProjectConfigurationEntity {

	/**
	 * ID of the project configuration.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private Long id;

	/**
	 * JSON string for metric importance.
	 */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Object metricConfiguration;

	/**
	 * The corresponding project.
	 */
	@OneToOne(mappedBy = "projectConfiguration", optional = false, orphanRemoval = false, cascade = CascadeType.PERSIST)
	@Setter(AccessLevel.NONE)
	private ProjectEntity project;

	/**
	 * Creates a new project configuration for the given project.
	 * @param project The project.
	 */
	public ProjectConfigurationEntity(final ProjectEntity project) {
		this.project = project;
	}
}
