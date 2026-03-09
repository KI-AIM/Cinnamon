package de.kiaim.cinnamon.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Import summary for all configurations contained in a file.
 *
 * @author Daniel Preciado-Marquez
 */
@RequiredArgsConstructor
@Getter @Setter
public class ConfigurationImportSummary {

	/**
	 * Parameters used for the import.
	 */
	private final ConfigurationImportParameters parameters;

	/**
	 * If the import was successful.
	 */
	private ConfigurationImportStatus status = ConfigurationImportStatus.SUCCESS;

	/**
	 * Set containing the import summary for each configuration.
	 */
	private final Set<ConfigurationImportSummaryPart> configurationImportSummaries = new HashSet<>();

	/**
	 * Adds a successful import to the import summary.
	 *
	 * @param configurationName The name of the configuration that was imported.
	 */
	public void addSuccess(final String configurationName) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.SUCCESS, null));
		updateStatus();
	}

	/**
	 * Adds an ignored configuration to the import summary.
	 *
	 * @param configurationName The name of the configuration that was ignored.
	 */
	public void addIgnored(final String configurationName) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.IGNORED, null));
	}

	/**
	 * Adds an error to the import summary.
	 *
	 * @param configurationName The name of the configuration that caused the error.
	 * @param errorCode         The cause of the error.
	 */
	public void addError(final String configurationName, final String errorCode) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.ERROR, errorCode));
		updateStatus();
	}

	/**
	 * Updates the status of the import summary.
	 */
	private void updateStatus() {
		status = ConfigurationImportStatus.SUCCESS;
		if (configurationImportSummaries.stream()
		                                .anyMatch(summary ->
				                                          summary.getStatus() == ConfigurationImportPartStatus.ERROR)) {
			status = parameters.isAllowPartialImport() ? ConfigurationImportStatus.PARTIAL_ERROR
			                                           : ConfigurationImportStatus.ERROR;
		}
	}

	/**
	 * Status of the configuration import.
	 */
	public enum ConfigurationImportStatus {
		/**
		 * All configurations were imported successfully.
		 */
		SUCCESS,
		/**
		 * At least one configuration was imported unsuccessfully, and partial import is allowed.
		 * This does not say anything about the number of successful imports.
		 * If the import of all configurations failed, the status is still PARTIAL_ERROR if partial import is allowed.
		 */
		PARTIAL_ERROR,
		/**
		 * At least one configuration was imported unsuccessfully, and partial import is not allowed.
		 */
		ERROR,
	}

	/**
	 * Status of the import of a single configuration.
	 */
	public enum ConfigurationImportPartStatus {
		/**
		 * The configuration was imported successfully.
		 */
		SUCCESS,
		/**
		 * The configuration was ignored because it was not selected in the import parameters.
		 */
		IGNORED,
		/**
		 * The configuration import failed.
		 */
		ERROR,
	}

	/**
	 * Import summary for a single configuration.
	 *
	 * @author Daniel Preciado-Marquez
	 */
	@AllArgsConstructor
	@Getter @Setter
	public static class ConfigurationImportSummaryPart {

		/**
		 * The name of the configuration.
		 */
		private String configurationName;

		/**
		 * Status of the configuration import.
		 */
		private ConfigurationImportPartStatus status;

		/**
		 * Null if the import was successful or ignored.
		 * Contains the error code if the import failed.
		 */
		@Nullable
		private String errorCode;
	}
}
