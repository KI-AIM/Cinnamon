package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.CsvFileConfiguration;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link CsvFileConfigurationEntity} and {@link CsvFileConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CsvFileConfigurationMapper {
	CsvFileConfiguration toDto(CsvFileConfigurationEntity entity);
	CsvFileConfigurationEntity toEntity(CsvFileConfiguration configuration);
}
