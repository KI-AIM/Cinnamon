package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Entity representing a planned or running external process like the anonymization.
 * TODO move the configuration into a separate object, add directly to the project and reference here
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter @Setter
@NoArgsConstructor
public abstract class ExternalProcessEntity extends BackgroundProcessEntity {

	/**
	 * Associated step of the process.
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Step step;

	/**
	 * The configuration for this process.
	 */
	@Column(length = Integer.MAX_VALUE)
	@Nullable
	private String configuration;

	/**
	 * Detailed status information.
	 * Can have any form.
	 */
	@Column(length = 1000)
	@Nullable
	private String status;

	/**
	 * Gets the corresponding execution step.
	 */
	public ExecutionStepEntity getExecutionStep() {
		return (ExecutionStepEntity) this.getOwner();
	}

	/**
	 * Sets the corresponding execution step.
	 */
	public void setExecutionStep(final ExecutionStepEntity executionStep) {
		this.setOwner(executionStep);
	}

	/**
	 * Returns the corresponding project.
	 * @return The corresponding project.
	 */
	public ProjectEntity getProject() {
		return getExecutionStep().getProject();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void reset() {
		super.reset();
		this.status = null;
	}
}
