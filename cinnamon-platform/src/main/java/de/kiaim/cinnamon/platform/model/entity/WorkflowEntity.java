package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity representing a workflow in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public class WorkflowEntity {

	/**
	 * Database ID of the workflow.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * ID of the workflow for external identification.
	 */
	@Column(nullable = false, unique = true)
	private UUID workflowId;

	/**
	 * User owning the workflow.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_email")
	private UserEntity user;

	/**
	 * Project associated with the workflow.
	 */
	@OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "project_id", unique = true, nullable = false)
	private ProjectEntity project;

	public void setUser(final UserEntity newUser) {
		final UserEntity oldUser = this.user;
		this.user = newUser;
		if (oldUser != null) {
			oldUser.getWorkflows().remove(this);
		}
		if (newUser != null && !newUser.getWorkflows().contains(this)) {
			newUser.getWorkflows().add(this);
		}
	}

	public void setProject(final ProjectEntity newProject) {
		final ProjectEntity oldProject = this.project;
		this.project = newProject;
		if (oldProject != null && oldProject.getWorkflow() == this) {
			oldProject.setWorkflow(null);
		}
		if (newProject != null && newProject.getWorkflow() != this) {
			newProject.setWorkflow(this);
		}
	}

}
