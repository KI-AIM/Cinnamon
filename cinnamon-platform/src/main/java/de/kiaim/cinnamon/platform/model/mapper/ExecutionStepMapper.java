package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.ExecutionStepInformation;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ExternalProcessMapper.class})
public interface ExecutionStepMapper {
	ExecutionStepInformation toDto(ExecutionStepEntity entity);
}
