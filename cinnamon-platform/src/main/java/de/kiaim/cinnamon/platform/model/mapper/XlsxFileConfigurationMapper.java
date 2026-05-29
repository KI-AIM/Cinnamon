package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.XlsxFileConfiguration;
import de.kiaim.cinnamon.platform.model.entity.XlsxFileConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link XlsxFileConfigurationEntity} and {@link XlsxFileConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface XlsxFileConfigurationMapper {
	XlsxFileConfiguration toDto(XlsxFileConfigurationEntity entity);
	XlsxFileConfigurationEntity toEntity(XlsxFileConfiguration configuration);
}
