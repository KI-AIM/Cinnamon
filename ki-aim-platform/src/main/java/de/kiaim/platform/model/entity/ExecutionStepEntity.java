package de.kiaim.platform.model.entity;

import de.kiaim.platform.converter.StageAttributeConverter;
import de.kiaim.platform.model.configuration.Job;
import de.kiaim.platform.model.configuration.Stage;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
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
@Getter @Setter
public class ExecutionStepEntity extends ProcessOwner {

	/**
	 * Index of the stage within the pipeline.
	 */
	private Integer stageIndex;

	/**
	 * The stage.
	 */
	@Column(nullable = false)
	@Convert(converter = StageAttributeConverter.class)
	private Stage stage;

	/**
	 * The status of the external processing.
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus status = ProcessStatus.NOT_STARTED;

	/**
	 * Index of the current job.
	 * Null if not running.
	 */
	@Nullable
	private Integer currentProcessIndex;

	/**
	 * Processes in this step.
	 */
	@OneToMany(mappedBy = "owner", orphanRemoval = true, cascade = CascadeType.ALL)
	@OrderBy("jobIndex")
	private List<BackgroundProcessEntity> processes = new ArrayList<>();

	/**
	 * The corresponding pipeline.
	 */
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "pipeline_id", nullable = false)
	private PipelineEntity pipeline;

	/**
	 * Adds a new process to this step.
	 * @param externalProcess The process to be added.
	 */
	public void addProcess(final ExternalProcessEntity externalProcess) {
		if (!this.containsJob(externalProcess.getJob())) {
			externalProcess.setJobIndex(processes.size());
			externalProcess.setExecutionStep(this);
			processes.add(externalProcess);
		}
	}

	/**
	 * Returns the process for the given Step.
	 *
	 * @param job The job.
	 * @return The process.
	 */
	public Optional<ExternalProcessEntity> getProcess(final Job job) {
		for (final ExternalProcessEntity externalProcess : getProcesses()) {
			if (externalProcess.getJob().equals(job)) {
				return Optional.of(externalProcess);
			}
		}

		return Optional.empty();
	}

	public ExternalProcessEntity getProcess(final Integer index) {
		return (ExternalProcessEntity) processes.get(index);
	}

	@Nullable
	public ExternalProcessEntity getCurrentProcess() {
		if (currentProcessIndex == null) {
			return null;
		}
		return getProcess(currentProcessIndex);
	}

	/**
	 * {@inheritDoc}
	 */
	public ProjectEntity getProject() {
		return pipeline.getProject();
	}

	public List<ExternalProcessEntity> getProcesses() {
		List<ExternalProcessEntity> processes = new ArrayList<>();
		for (final BackgroundProcessEntity externalProcess : this.processes) {
			processes.add((ExternalProcessEntity) externalProcess);
		}
		return processes;
	}

	/**
	 * Checks if this stage contains a job with the given name.
	 * @param job The name of the job.
	 * @return If this stage contains a job with the given name.
	 */
	private boolean containsJob(final Job job) {
		for (final ExternalProcessEntity externalProcess : getProcesses()) {
			if (externalProcess.getJob().equals(job)) {
				return true;
			}
		}
		return false;
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
