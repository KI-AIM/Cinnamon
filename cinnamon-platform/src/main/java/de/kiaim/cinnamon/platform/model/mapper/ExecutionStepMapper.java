package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.dto.ExecutionStepInformation;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ExternalProcessMapper.class})
public interface ExecutionStepMapper {
	@Mapping(target = "stageName", source = "entity.stage.stageName")
	ExecutionStepInformation toDto(ExecutionStepEntity entity);
}
