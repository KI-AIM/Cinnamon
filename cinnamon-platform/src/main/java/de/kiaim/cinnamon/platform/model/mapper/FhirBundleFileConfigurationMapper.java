package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.FhirBundleFileConfiguration;
import de.kiaim.cinnamon.platform.model.entity.FhirBundleFileConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link FhirBundleFileConfigurationEntity} and {@link FhirBundleFileConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FhirBundleFileConfigurationMapper {
	FhirBundleFileConfiguration toDto(FhirBundleFileConfigurationEntity entity);
	FhirBundleFileConfigurationEntity toEntity(FhirBundleFileConfiguration configuration);
}
