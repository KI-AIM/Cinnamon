package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Entity representing an executable step.
 * Groups multiple Processes that are executed sequentially and depend on the previous process.
 */
@Entity
public class ExecutionStepEntity {

	/** The ID. */
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Index of the stage within the pipeline.
	 */
	@Getter @Setter
	private Integer stageIndex;

	/**
	 * Associated step of the process.
	 */
	@JsonIgnore
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@Getter @Setter
	private Step step;

	/**
	 * The status of the external processing.
	 */
	@Schema(description = "The status of the external processing.")
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@Getter @Setter
	private ProcessStatus status = ProcessStatus.NOT_STARTED;

	/**
	 * Index of the current job.
	 * Null if not running.
	 */
	@Getter @Setter
	@Nullable
	private Integer currentProcessIndex;

	/**
	 * Processes in this step.
	 */
	@OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@OrderBy("jobIndex")
	@Getter
	private final List<ExternalProcessEntity> processes = new ArrayList<>();

	/**
	 * The corresponding pipeline.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "pipeline_id", nullable = false)
	@Getter @Setter
	private PipelineEntity pipeline;

	/**
	 * Adds a new process to this step.
	 * @param externalProcess The process to be added.
	 */
	public void addProcess(final ExternalProcessEntity externalProcess) {
		if (!processes.contains(externalProcess)) {
			externalProcess.setJobIndex(processes.size());
			externalProcess.setExecutionStep(this);
			processes.add(externalProcess);
		}
	}

	/**
	 * Returns the process for the given Step.
	 * @param step The Step.
	 * @return The process.
	 */
	public Optional<ExternalProcessEntity> getProcess(final Step step) {
		for (final ExternalProcessEntity externalProcess : processes) {
			if (externalProcess.getStep().equals(step)) {
				return Optional.of(externalProcess);
			}
		}

		return Optional.empty();
	}

	public ExternalProcessEntity getProcess(final Integer index) {
		return processes.get(index);
	}

	/**
	 * Returns the step of the current process.
	 * @return The step of the current process.
	 */
	@Nullable
	public Step getCurrentStep() {
		final var currentProcess = getCurrentProcess();
		if (currentProcess != null) {
			return currentProcess.getStep();
		} else {
			return null;
		}
	}

	@JsonIgnore
	@Nullable
	public ExternalProcessEntity getCurrentProcess() {
		if (currentProcessIndex == null) {
			return null;
		}
		return getProcess(currentProcessIndex);
	}

	@JsonIgnore
	public ProjectEntity getProject() {
		return pipeline.getProject();
	}

	/**
	 * Validates the status matches the rest of the stages state.
	 */
	@PrePersist @PreUpdate
	private void validateStatus() {
		if (this.status == ProcessStatus.RUNNING && this.currentProcessIndex == null) {
			throw new IllegalStateException("The stage is running but the current job is not set.");
		}
		if (this.status != ProcessStatus.RUNNING && this.currentProcessIndex != null) {
			throw new IllegalStateException("The current job is set but the stage is not running.");
		}
	}
}
