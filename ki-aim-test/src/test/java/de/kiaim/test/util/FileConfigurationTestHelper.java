package de.kiaim.test.util;

import de.kiaim.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.platform.model.entity.FileConfigurationEntity;
import de.kiaim.platform.model.entity.XlsxFileConfigurationEntity;
import de.kiaim.platform.model.file.CsvFileConfiguration;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.model.file.XlsxFileConfiguration;

public class FileConfigurationTestHelper {
	public static FileConfiguration generateFileConfiguration() {
		return generateFileConfiguration(true);
	}

	public static FileConfiguration generateFileConfiguration(final boolean hasHeader) {
		return new FileConfiguration(
				FileType.CSV,
				new CsvFileConfiguration(",", "\n", '"', hasHeader),
				new XlsxFileConfiguration(hasHeader)
		);
	}

	public static FileConfigurationEntity generateFileConfiguration(final FileType fileType, final boolean hasHeader) {
		final var dto = generateFileConfiguration(hasHeader);

		return switch (fileType) {
			case CSV -> new CsvFileConfigurationEntity(dto.getCsvFileConfiguration());
			case FHIR -> new FhirFileConfigurationEntity();
			case XLSX -> new XlsxFileConfigurationEntity(dto.getXlsxFileConfiguration());
		};
	}
}
