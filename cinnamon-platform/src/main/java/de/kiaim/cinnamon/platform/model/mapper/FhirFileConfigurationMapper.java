package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.FhirFileConfiguration;
import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link FhirFileConfigurationEntity} and {@link FhirFileConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FhirFileConfigurationMapper {
	FhirFileConfiguration toDto(FhirFileConfigurationEntity entity);
	FhirFileConfigurationEntity toEntity(FhirFileConfiguration configuration);
}
