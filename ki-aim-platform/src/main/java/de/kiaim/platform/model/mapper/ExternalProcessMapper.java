package de.kiaim.platform.model.mapper;

import de.kiaim.platform.exception.BadStateException;
import de.kiaim.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.platform.exception.InternalInvalidStateException;
import de.kiaim.platform.exception.InternalMissingHandlingException;
import de.kiaim.platform.model.dto.ExternalProcessInformation;
import de.kiaim.platform.model.entity.DataProcessingEntity;
import de.kiaim.platform.model.entity.EvaluationProcessingEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.Step;
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

	@Mapping(target = "processSteps", source = "entity", qualifiedByName = "processSteps")
	public abstract ExternalProcessInformation toDto(ExternalProcessEntity entity);

	@Named("processSteps")
	protected List<Step> toProcessed(ExternalProcessEntity entity) {
		if (entity instanceof DataProcessingEntity dataProcessing) {
			return dataProcessing.getDataSet() != null ? dataProcessing.getDataSet().getProcessed() : null;
		} else if (entity instanceof EvaluationProcessingEntity evaluation) {
			try {
				return processService.getDataSet(evaluation).getProcessed();
			} catch (final InternalApplicationConfigurationException | BadStateException | InternalInvalidStateException |
			         InternalMissingHandlingException e) {
				log.error("Failed to convert to DTO!", e);
			}
		}
		return null;
	}
}
