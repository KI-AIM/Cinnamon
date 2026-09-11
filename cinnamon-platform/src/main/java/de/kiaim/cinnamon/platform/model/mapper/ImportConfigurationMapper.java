package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.ImportConfigurationDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.ImportConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ImportConfigurationMapper {

	@Mapping(target = "attributes", ignore = true)
	ImportConfigurationDTO toDto(ImportConfigurationEntity entity);

	@Mapping(target = "id", ignore = true)
	void updateEntity(@MappingTarget ImportConfigurationEntity entity, ImportConfigurationDTO dto);

}
