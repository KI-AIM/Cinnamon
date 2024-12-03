package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonGetter;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Entity that contains the result of the evaluation between the original data set and the target data set.
 */
@Entity
public class EvaluationProcessingEntity extends ExternalProcessEntity {

	@JsonGetter("processSteps")
	public List<Step> processSteps() {
		var a = getExecutionStep().getPipeline().getStageByStep(Step.EXECUTION).getProcesses();
		for (int i = a.size() - 1; i >= 0; i--) {
			var p = a.get(i);
			if (p.getExternalProcessStatus() == ProcessStatus.FINISHED) {
				if (p instanceof DataProcessingEntity d) {
					return d.getDataSet().getProcessed();
				}
			}
		}

		return List.of();
	}
}
