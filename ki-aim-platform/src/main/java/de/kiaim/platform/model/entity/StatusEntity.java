package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class representing the status of a project.
 */
@Entity
@Getter
@NoArgsConstructor
public class StatusEntity {

	/**
	 * ID of the status.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Current step of the project
	 */
	@Setter
	@Column(nullable = false)
	private Step currentStep = Step.UPLOAD;

	/**
	 * The status of the external processing.
	 */
	@Setter
	@Column(nullable = false)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_REQUIRED;

	/**
	 * The corresponding project.
	 */
	@OneToOne(mappedBy = "status", optional = false, fetch = FetchType.LAZY, orphanRemoval = false, cascade = CascadeType.ALL)
	private ProjectEntity project;

	public StatusEntity(final ProjectEntity project) {
		this.project = project;
	}
}
