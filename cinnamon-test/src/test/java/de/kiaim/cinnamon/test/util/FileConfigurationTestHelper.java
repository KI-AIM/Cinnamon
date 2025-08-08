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
		return new FileConfiguration(
				FileType.CSV,
				new CsvFileConfiguration(",", "\n", '"', hasHeader),
				new XlsxFileConfiguration(hasHeader),
				new FhirFileConfiguration()
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
