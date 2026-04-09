package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.DatasetConfiguration;
import de.kiaim.cinnamon.platform.model.entity.DatasetConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * Mapper for {@link DatasetConfigurationEntity} and {@link DatasetConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DatasetConfigurationMapper {
	DatasetConfiguration toDto(DatasetConfigurationEntity entity);
	DatasetConfigurationEntity toEntity(DatasetConfiguration dto);
	void updateEntity(@MappingTarget DatasetConfigurationEntity entity, DatasetConfiguration dto);
}
