package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.configuration.Stage;
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
	 * Checks if this pipeline contains a stage with the given name.
	 *
	 * @param stageName The name fo the stage.
	 * @return If this pipeline contains a stage with the given name.
	 */
	public boolean containsStage(final Stage stageName) {
		for (final ExecutionStepEntity stage : stages) {
			if (stage.getStage().equals(stageName)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Adds the given stage to this pipeline if no stage with the given name exists.
	 * Otherwise, does nothing.
	 *
	 * @param stage         The the stage.
	 * @param executionStep The stage.
	 */
	public void addStage(final Stage stage, final ExecutionStepEntity executionStep) {
		if (!containsStage(stage)) {
			executionStep.setStageIndex(stages.size());
			executionStep.setPipeline(this);
			executionStep.setStage(stage);
			stages.add(executionStep);
		}
	}

	/**
	 * Return the stage for the given step.
	 *
	 * @param stage The stage.
	 * @return The stage.
	 */
	@Nullable
	public ExecutionStepEntity getStageByStep(final Stage stage) {
		for (final ExecutionStepEntity stepEntity : this.stages) {
			if (stepEntity.getStage().equals(stage)) {
				return stepEntity;
			}
		}

		return null;
	}

	/**
	 * Return the stage for the given index.
	 *
	 * @param index The index of the stage.
	 * @return The stage.
	 */
	public ExecutionStepEntity getStageByIndex(final Integer index) {
		return stages.get(index);
	}

}
