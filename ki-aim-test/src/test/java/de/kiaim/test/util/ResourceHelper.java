package de.kiaim.test.util;

import de.kiaim.test.platform.TestModelHelper;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

public class ResourceHelper {

	public static MockMultipartFile loadCsvFile() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("test.csv"));
	}

	public static MockMultipartFile loadCsvFileWithErrors() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("testWithErrors.csv"));
	}
}
