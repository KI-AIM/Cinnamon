package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.dto.StepConfigurationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StepConfigurationMapper {
	StepConfigurationResponse map(ExternalConfiguration externalConfiguration);
}
