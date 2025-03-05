package de.kiaim.platform.model.mapper;

import de.kiaim.platform.model.dto.ExecutionStepInformation;
import de.kiaim.platform.model.entity.ExecutionStepEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ExternalProcessMapper.class})
public interface ExecutionStepMapper {
	ExecutionStepInformation toDto(ExecutionStepEntity entity);
}
