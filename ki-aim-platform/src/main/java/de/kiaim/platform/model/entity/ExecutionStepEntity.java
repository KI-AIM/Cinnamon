package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

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
	 * The current step of the execution.
	 * Null if not running.
	 */
	@Enumerated(EnumType.STRING)
	@Getter @Setter
	@Nullable
	private Step currentStep;

	/**
	 * Processes in this step.
	 */
	@OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "step")
	@Getter
	private final Map<Step, ExternalProcessEntity> processes = new HashMap<>();

	/**
	 * The corresponding pipeline.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "pipeline_id", nullable = false)
	@Getter @Setter
	private PipelineEntity pipeline;

	/**
	 * Adds a new process for the given step to this step.
	 * @param step The step.
	 * @param externalProcess The process to be added.
	 */
	public void putExternalProcess(final Step step, final ExternalProcessEntity externalProcess) {
		if (!processes.containsKey(step)) {
			externalProcess.setExecutionStep(this);
			externalProcess.setStep(step);
			processes.put(step, externalProcess);
		}
	}
}
