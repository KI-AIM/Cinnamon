package de.kiaim.platform.model.file;

import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public enum FileType {
	CSV(new HashSet<>(List.of(".csv"))),
	FHIR(new HashSet<>(List.of(".json", ".xml"))),
	XLSX(new HashSet<>(List.of(".xlsx")));

	/**
	 * Supported file extensions.
	 */
	private final Set<String> fileExtensions;

	FileType(final Set<String> fileExtensions) {
		this.fileExtensions = fileExtensions;
	}

	public static class Values {
		public static final String CSV = "CSV";
		public static final String FHIR = "FHIR";
		public static final String XLSX = "XLSX";
	}
}
