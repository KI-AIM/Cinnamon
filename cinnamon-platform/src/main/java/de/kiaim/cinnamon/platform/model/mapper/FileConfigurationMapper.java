package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.model.configuration.data.file.CsvFileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FhirFileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.XlsxFileConfiguration;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.XlsxFileConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(
		componentModel = MappingConstants.ComponentModel.SPRING,
		uses = {
				CsvFileConfigurationMapper.class,
				FhirFileConfigurationMapper.class,
				XlsxFileConfigurationMapper.class
		}
)
public abstract class FileConfigurationMapper {

	public FileConfiguration toDto(FileConfigurationEntity entity) {
		if (entity == null) {
			return null;
		}

		FileConfiguration dto = new FileConfiguration();
		dto.setFileType(entity.getFileType());

		if (entity instanceof CsvFileConfigurationEntity csvEntity) {
			dto.setCsvFileConfiguration(toDto(csvEntity));
		} else if (entity instanceof XlsxFileConfigurationEntity xlsxEntity) {
			dto.setXlsxFileConfiguration(toDto(xlsxEntity));
		} else if (entity instanceof FhirFileConfigurationEntity fhirEntity) {
			dto.setFhirFileConfiguration(toDto(fhirEntity));
		}

		return dto;
	}

	public FileConfigurationEntity toEntity(FileConfiguration configuration) {
		if (configuration == null) {
			return null;
		}

		if (configuration.getFileType() == null) {
			return null;
		}

		return switch (configuration.getFileType()) {
			case CSV -> toEntity(configuration.getCsvFileConfiguration());
			case XLSX -> toEntity(configuration.getXlsxFileConfiguration());
			case FHIR -> toEntity(configuration.getFhirFileConfiguration());
		};
	}

	protected abstract CsvFileConfiguration toDto(CsvFileConfigurationEntity entity);

	protected abstract XlsxFileConfiguration toDto(XlsxFileConfigurationEntity entity);

	protected abstract FhirFileConfiguration toDto(FhirFileConfigurationEntity entity);

	protected abstract CsvFileConfigurationEntity toEntity(CsvFileConfiguration configuration);

	protected abstract XlsxFileConfigurationEntity toEntity(XlsxFileConfiguration configuration);

	protected abstract FhirFileConfigurationEntity toEntity(FhirFileConfiguration configuration);
}
