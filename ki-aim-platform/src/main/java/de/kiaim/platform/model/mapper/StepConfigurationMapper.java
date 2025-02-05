package de.kiaim.platform.model.mapper;

import de.kiaim.platform.model.configuration.ExternalServer;
import de.kiaim.platform.model.configuration.ExternalEndpoint;
import de.kiaim.platform.model.dto.StepConfigurationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StepConfigurationMapper {
	@Mapping(target= "algorithmEndpoint", source = "externalEndpoint.configuration.algorithmEndpoint")
	StepConfigurationResponse map(ExternalServer externalServer,
	                              ExternalEndpoint externalEndpoint);
}
