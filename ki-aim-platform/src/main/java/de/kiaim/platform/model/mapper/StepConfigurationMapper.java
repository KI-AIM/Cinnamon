package de.kiaim.platform.model.mapper;

import de.kiaim.platform.model.configuration.ExternalServer;
import de.kiaim.platform.model.configuration.ExternalEndpoint;
import de.kiaim.platform.model.dto.StepConfigurationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StepConfigurationMapper {
	StepConfigurationResponse map(ExternalServer externalServer,
	                              ExternalEndpoint externalEndpoint);
}
