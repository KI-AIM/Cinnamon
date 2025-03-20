package de.kiaim.cinnamon.test.util;

import de.kiaim.cinnamon.test.platform.TestModelHelper;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ResourceHelper {

	public static MockMultipartFile loadCsvFile() throws IOException {
		return loadCsvFile("file");
	}

	public static MockMultipartFile loadCsvFile(final String paramName) throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile(paramName, "file.csv", null,
		                             classLoader.getResourceAsStream("test.csv"));
	}

	public static String loadCsvFileAsString() throws IOException {
		return unifyLineEndings(new String(loadCsvFile().getBytes(), StandardCharsets.UTF_8));
	}

	public static MockMultipartFile loadCsvFileWithErrors() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("testWithErrors.csv"));
	}

	public static String loadCsvFileWithErrorsAsString() throws IOException {
		return unifyLineEndings(new String(loadCsvFileWithErrors().getBytes(), StandardCharsets.UTF_8));
	}

	public static String unifyLineEndings(final String value) {
		return value.replaceAll("\r\n", "\n")
		            .replaceAll("\n", "\r\n");
	}
}
