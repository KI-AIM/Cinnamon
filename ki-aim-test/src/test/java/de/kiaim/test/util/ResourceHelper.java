package de.kiaim.test.util;

import de.kiaim.test.platform.TestModelHelper;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ResourceHelper {

	public static MockMultipartFile loadCsvFile() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("test.csv"));
	}

	public static String loadCsvFileAsString() throws IOException {
		return new String(loadCsvFile().getBytes(), StandardCharsets.UTF_8);
	}

	public static MockMultipartFile loadCsvFileWithErrors() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("testWithErrors.csv"));
	}

	public static String loadCsvFileWithErrorsAsString() throws IOException {
		return new String(loadCsvFileWithErrors().getBytes(), StandardCharsets.UTF_8);
	}
}
