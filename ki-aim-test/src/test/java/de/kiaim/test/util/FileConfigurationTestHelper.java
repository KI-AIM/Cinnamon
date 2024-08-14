package de.kiaim.test.util;

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
}
