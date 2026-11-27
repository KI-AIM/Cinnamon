package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.*;
import de.kiaim.cinnamon.platform.model.entity.*;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(
		componentModel = MappingConstants.ComponentModel.SPRING,
		uses = {
				CsvFileConfigurationMapper.class,
				FhirBundleFileConfigurationMapper.class,
				XlsxFileConfigurationMapper.class
		}
)
public abstract class FileConfigurationMapper {

	public @Nullable FileConfiguration toDto(@Nullable FileConfigurationEntity entity) {
		if (entity == null) {
			return null;
		}

		FileConfiguration dto = new FileConfiguration();
		dto.setFileType(entity.getFileType());

		// No conversion for FhirNdjsonFileConfigurationEntity needed
		if (entity instanceof CsvFileConfigurationEntity csvEntity) {
			dto.setCsvFileConfiguration(toDto(csvEntity));
		} else if (entity instanceof XlsxFileConfigurationEntity xlsxEntity) {
			dto.setXlsxFileConfiguration(toDto(xlsxEntity));
		} else if (entity instanceof FhirBundleFileConfigurationEntity fhirEntity) {
			dto.setFhirBundleFileConfiguration(toDto(fhirEntity));
		}

		return dto;
	}

	public @Nullable FileConfigurationEntity toEntity(@Nullable FileConfiguration configuration) {
		if (configuration == null) {
			return null;
		}

		if (configuration.getFileType() == null) {
			return null;
		}

		var result = switch (configuration.getFileType()) {
			case CSV -> toEntity(configuration.getCsvFileConfiguration());
			case XLSX -> toEntity(configuration.getXlsxFileConfiguration());
			case FHIR_BUNDLE -> toEntity(configuration.getFhirBundleFileConfiguration());
			case FHIR_NDJSON -> new FhirNdjsonFileConfigurationEntity();
		};

		return result;
	}

	protected abstract CsvFileConfiguration toDto(CsvFileConfigurationEntity entity);

	protected abstract XlsxFileConfiguration toDto(XlsxFileConfigurationEntity entity);

	protected abstract FhirBundleFileConfiguration toDto(FhirBundleFileConfigurationEntity entity);

	protected abstract CsvFileConfigurationEntity toEntity(CsvFileConfiguration configuration);

	protected abstract XlsxFileConfigurationEntity toEntity(XlsxFileConfiguration configuration);

	protected abstract FhirBundleFileConfigurationEntity toEntity(FhirBundleFileConfiguration configuration);
}
