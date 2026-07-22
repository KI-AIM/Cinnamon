package de.kiaim.cinnamon.platform.model.mapper;


import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.platform.model.entity.DataSourceConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DataSourceConfigurationMapper {

	DataSourceConfiguration toDto(DataSourceConfigurationEntity entity);

	@Mapping(target = "id", ignore = true)
	void updateEntity(@MappingTarget DataSourceConfigurationEntity entity, DataSourceConfiguration dto);
}
