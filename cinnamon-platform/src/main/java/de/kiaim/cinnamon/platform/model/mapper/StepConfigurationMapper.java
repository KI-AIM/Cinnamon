package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.dto.StepConfigurationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StepConfigurationMapper {
	@Mapping(target = "urlClient", source = "externalServer.urlClient")
	StepConfigurationResponse map(ExternalConfiguration externalConfiguration);
}
