package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * Entity that contains the result of the evaluation between the original data set and the target data set.
 */
@Entity
@Getter
public class EvaluationProcessingEntity extends ExternalProcessEntity {

	@ManyToOne
	private DataProcessingEntity target = null;


	/**
	 * Returns the process that is used as a target for the comparison with the original data set.
	 * @return The DataProcessingEntity
	 */
	@Nullable
	public DataProcessingEntity getTargetProcess() {
		var a = getExecutionStep().getPipeline().getStageByStep(Step.EXECUTION).getProcesses();
		for (int i = a.size() - 1; i >= 0; i--) {
			var p = a.get(i);
			if (p.getExternalProcessStatus() == ProcessStatus.FINISHED) {
				if (p instanceof DataProcessingEntity d) {
					return d;
				}
			}
		}

		return null;
	}

}
