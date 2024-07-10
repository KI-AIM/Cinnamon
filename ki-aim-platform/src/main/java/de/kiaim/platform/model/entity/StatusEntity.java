package de.kiaim.platform.model.entity;

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
	 * If external processing has finished.
	 * Also true if the current step does not require any external processing.
	 */
	@Setter
	@Column(nullable = false)
	private Boolean finishedExternalProcessing = true;

	/**
	 * The corresponding project.
	 */
	@OneToOne(optional = false, fetch = FetchType.LAZY, orphanRemoval = false, cascade = CascadeType.ALL)
	@JoinColumn(name = "project_id", referencedColumnName = "id")
	private ProjectEntity project;

	public StatusEntity(final ProjectEntity project) {
		this.project = project;
	}
}
