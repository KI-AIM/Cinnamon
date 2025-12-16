package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.model.dto.PipelineInformation;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Mapper for {@link PipelineEntity} to {@link PipelineInformation}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ExecutionStepMapper.class})
public abstract class PipelineMapper {

	/**
	 * Converts a pipeline entity into a DTO.
	 *
	 * @param entity The entity.
	 * @return The DTO.
	 */
	@Mapping(target = "currentStageIndex", source = "entity.stages", qualifiedByName = "currentStageIndex")
	public abstract PipelineInformation toDto(PipelineEntity entity);

	/**
	 * Gets the index of the currently running or scheduled stage from the given list of stages.
	 * Returns null if no stage is running or scheduled.
	 *
	 * @param stages The stages of the pipeline.
	 * @return Index of the running stage or null.
	 */
	@Named("currentStageIndex")
	@Nullable
	protected Integer toCurrentStageIndex(final List<ExecutionStepEntity> stages) {
		for (int i = 0; i < stages.size(); i++) {
			final ExecutionStepEntity stage = stages.get(i);
			if (stage.getStatus() == ProcessStatus.RUNNING || stage.getStatus() == ProcessStatus.SCHEDULED) {
				return i;
			}
		}

		return null;
	}

}
