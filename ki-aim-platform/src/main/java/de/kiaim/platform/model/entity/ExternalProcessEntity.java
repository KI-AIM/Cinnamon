package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Entity representing a planned or running external process like the anonymization.
 * TODO move the configuration into a separate object, add directly to the project and reference here
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor
public abstract class ExternalProcessEntity {

	/**
	 * ID of the process.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Index of the job within the stage.
	 */
	@Getter @Setter
	private Integer jobIndex;

	/**
	 * Associated step of the process.
	 */
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Step step;

	/**
	 * The status of the external processing.
	 */
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_STARTED;

	/**
	 * Time when the process has been scheduled.
	 * Null if status is not SCHEDULED.
	 */
	@Setter
	@Nullable
	private Timestamp scheduledTime;

	/**
	 * URL to start the process.
	 * Null if status is not SCHEDULED.
	 */
	@Setter
	@Nullable
	private String processUrl;

	/**
	 * The configuration for this process.
	 */
	@Column(length = Integer.MAX_VALUE)
	@Setter
	@Nullable
	private String configuration;

	/**
	 * Process id in the module.
	 */
	@Setter
	@Nullable
	private String externalId;

	/**
	 * The corresponding execution step.
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@Setter
	private ExecutionStepEntity executionStep;

	/**
	 * Detailed status information.
	 * Can have any form.
	 */
	@Column(length = 1000)
	@Setter
	@Nullable
	private String status;

	/**
	 * Additional files created during the process.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name = "filename")
	@Lob
	private final Map<String, byte[]> additionalResultFiles = new HashMap<>();

	/**
	 * Checks if this process should be skipped.
	 * @return If this process should be skipped.
	 */
	public boolean shouldBeSkipped() {
		return Objects.equals(processUrl, "skip");
	}

	/**
	 * Returns the corresponding project.
	 * @return The corresponding project.
	 */
	public ProjectEntity getProject() {
		return executionStep.getProject();
	}
}
