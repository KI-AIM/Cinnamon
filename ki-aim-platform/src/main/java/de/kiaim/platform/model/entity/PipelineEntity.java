package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity for representing a pipeline.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
public class PipelineEntity {

	/**
	 * The ID.
	 */
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Index of the pipeline within the project.
	 */
	@Getter @Setter
	private Integer pipelineIndex;

	/**
	 * Stages of this pipeline.
	 */
	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "pipeline")
	@OrderBy("stageIndex")
	@Getter
	private final List<ExecutionStepEntity> stages = new ArrayList<>();

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@Getter @Setter
	private ProjectEntity project;

	/**
	 * Checks if this pipeline contains a stage for the given step.
	 * @param step The step.
	 * @return If this pipeline contains a stage for the given step.
	 */
	public boolean containsStage(final Step step) {
		for (final ExecutionStepEntity stage : stages) {
			if (stage.getStep().equals(step)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Adds the given stage to this pipeline if no stage for the given step exists.
	 * Otherwise, does nothing.
	 * @param step          The step of the stage.
	 * @param executionStep The stage.
	 */
	public void addStage(final Step step, final ExecutionStepEntity executionStep) {
		if (!containsStage(step)) {
			executionStep.setStageIndex(stages.size());
			executionStep.setPipeline(this);
			executionStep.setStep(step);
			stages.add(executionStep);
		}
	}

	/**
	 * Return the stage for the given step.
	 * @param step The step.
	 * @return The stage.
	 */
	@Nullable
	public ExecutionStepEntity getStageByStep(final Step step) {
		for (final ExecutionStepEntity stepEntity : this.stages) {
			if (stepEntity.getStep().equals(step)) {
				return stepEntity;
			}
		}

		return null;
	}

}
