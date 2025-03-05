package de.kiaim.cinnamon.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Class representing the status of a project.
 */
@Schema(description = "Status of a project.")
@Entity
@Getter
@NoArgsConstructor
public class StatusEntity {

	/**
	 * ID of the status.
	 */
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Mode the user has selected.
	 * Is null if the user did not select a mode.
	 */
	@Schema(description = "Mode the user has selected. Null if the user has not selected a mode.")
	@Setter
	@Nullable
	@Enumerated(EnumType.STRING)
	private Mode mode;

	/**
	 * Current step of the project
	 */
	@Schema(description = "The current step. Intermediate steps for the data configuration are NOT saved.")
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Step currentStep = Step.WELCOME;

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@OneToOne(mappedBy = "status", optional = false, orphanRemoval = false, cascade = CascadeType.ALL)
	private ProjectEntity project;

	public StatusEntity(final ProjectEntity project) {
		this.project = project;
	}
}
