package de.kiaim.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

/**
 * Entity representing a planned or running external process like the anonymization.
 */
@Entity
@Getter
@NoArgsConstructor
public class ExternalProcessEntity {

	/**
	 * ID of the process.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * The corresponding project.
	 */
	@OneToOne(mappedBy = "externalProcess", optional = false, fetch = FetchType.LAZY, orphanRemoval = false,
	          cascade = {})
	private ProjectEntity project;

	/**
	 * Links the given project with this external process.
	 * @param newProject The project to link.
	 */
	public void setProject(@Nullable final ProjectEntity newProject) {
		final ProjectEntity oldProject = this.project;
		this.project = newProject;
		if (oldProject != null && oldProject.getExternalProcess() == this) {
			oldProject.setExternalProcess(null);
		}
		if (newProject != null && newProject.getExternalProcess() != this) {
			newProject.setExternalProcess(this);
		}
	}
}
