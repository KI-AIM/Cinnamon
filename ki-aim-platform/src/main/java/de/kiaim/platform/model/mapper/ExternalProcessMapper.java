package de.kiaim.platform.model.mapper;

import de.kiaim.platform.exception.BadStateException;
import de.kiaim.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.platform.exception.InternalInvalidStateException;
import de.kiaim.platform.exception.InternalMissingHandlingException;
import de.kiaim.platform.model.configuration.Job;
import de.kiaim.platform.model.dto.ExternalProcessInformation;
import de.kiaim.platform.model.entity.DataProcessingEntity;
import de.kiaim.platform.model.entity.EvaluationProcessingEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.service.ProcessService;
import lombok.extern.log4j.Log4j2;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Log4j2
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class ExternalProcessMapper {

	@Autowired
	protected ProcessService processService;

	@Mapping(target = "step", source = "entity.job", qualifiedByName = "job")
	@Mapping(target = "processSteps", source = "entity", qualifiedByName = "processSteps")
	public abstract ExternalProcessInformation toDto(ExternalProcessEntity entity);

	@Named("job")
	protected String toJob(Job job) {
		return job.getName();
	}

	@Named("processSteps")
	protected List<String> toProcessed(ExternalProcessEntity entity) {
		List<Job> jobs = null;
		if (entity instanceof DataProcessingEntity dataProcessing) {
			jobs = dataProcessing.getDataSet() != null ? dataProcessing.getDataSet().getProcessed() : null;
		} else if (entity instanceof EvaluationProcessingEntity evaluation) {
			try {
				jobs = processService.getDataSet(evaluation).getProcessed();
			} catch (final InternalApplicationConfigurationException | BadStateException | InternalInvalidStateException |
			         InternalMissingHandlingException e) {
				log.error("Failed to convert to DTO!", e);
			}
		}

		if (jobs != null) {
			return jobs.stream().map(Job::getName).toList();
		} else {
			return null;
		}
	}
}
