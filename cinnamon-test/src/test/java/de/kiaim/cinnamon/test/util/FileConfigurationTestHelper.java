package de.kiaim.cinnamon.test.util;

import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.XlsxFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.file.*;

public class FileConfigurationTestHelper {
	public static FileConfiguration generateFileConfiguration() {
		return generateFileConfiguration(true);
	}

	public static FileConfiguration generateFileConfiguration(final boolean hasHeader) {
		return generateFileConfiguration(hasHeader, FileType.CSV);
	}

	public static FileConfiguration generateFileConfiguration(final FileType fileType) {
		return generateFileConfiguration(true, fileType);
	}

	public static FileConfiguration generateFileConfiguration(final boolean hasHeader, final FileType fileType) {
		return new FileConfiguration(
				fileType,
				new CsvFileConfiguration(",", "\n", '"', hasHeader),
				new XlsxFileConfiguration(hasHeader),
				new FhirFileConfiguration("Observation")
		);
	}

	public static FileConfigurationEntity generateFileConfiguration(final FileType fileType, final boolean hasHeader) {
		final var dto = generateFileConfiguration(hasHeader);

		return switch (fileType) {
			case CSV -> new CsvFileConfigurationEntity(dto.getCsvFileConfiguration());
			case FHIR -> new FhirFileConfigurationEntity(dto.getFhirFileConfiguration());
			case XLSX -> new XlsxFileConfigurationEntity(dto.getXlsxFileConfiguration());
		};
	}
}
