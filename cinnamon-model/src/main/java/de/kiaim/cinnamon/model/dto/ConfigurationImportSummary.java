package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import summary for all configurations contained in a file.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Import summary for all configurations contained in a file.")
@RequiredArgsConstructor
@Getter @Setter
public class ConfigurationImportSummary {

	/**
	 * Parameters used for the import.
	 */
	@Schema(description = "Parameters used for the import.")
	private final ConfigurationImportParameters parameters;

	/**
	 * If the import was successful.
	 */
	@Schema(description = "If the import was successful.")
	private ConfigurationImportStatus status = ConfigurationImportStatus.SUCCESS;

	/**
	 * Set containing the import summary for each configuration.
	 */
	@Schema(description = "Set containing the import summary for each configuration in the file.")
	private final Set<ConfigurationImportSummaryPart> configurationImportSummaries = new HashSet<>();

	/**
	 * Adds a successful import to the import summary.
	 *
	 * @param configurationName The name of the configuration that was imported.
	 */
	public void addSuccess(final String configurationName) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.SUCCESS, null,
				                                   null, null));
		updateStatus();
	}

	/**
	 * Adds an ignored configuration to the import summary.
	 *
	 * @param configurationName The name of the configuration that was ignored.
	 */
	public void addIgnored(final String configurationName) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.IGNORED, null,
				                                   null, null));
	}

	/**
	 * Adds an error to the import summary.
	 *
	 * @param configurationName The name of the configuration that caused the error.
	 * @param errorCode         The cause of the error.
	 * @param message           Human-readable error message.
	 */
	public void addError(final String configurationName, final String errorCode, final String message) {
		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.ERROR, errorCode,
				                                   message, null));
		updateStatus();
	}

	/**
	 * Adds an error to the import summary.
	 *
	 * @param configurationName The name of the configuration that caused the error.
	 * @param errorCode         The cause of the error.
	 * @param validationErrors  Map containing the paths of values that failed validation and the validation errors.
	 */
	public <T> void addError(final String configurationName,
	                         final String errorCode,
	                         final Set<ConstraintViolation<T>> validationErrors) {
		final Map<String, Set<String>> validationErrorsMap = validationErrors.stream()
		                                                                     .collect(Collectors.groupingBy(
				                                                                     a -> a.getPropertyPath()
				                                                                           .toString(),
				                                                                     Collectors.mapping(
						                                                                     ConstraintViolation::getMessage,
						                                                                     Collectors.toSet())));

		configurationImportSummaries.add(
				new ConfigurationImportSummaryPart(configurationName, ConfigurationImportPartStatus.ERROR, errorCode,
				                                   "Validation failed. See validation errors for more details.",
				                                   validationErrorsMap));
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
	@Schema(description = "Import summary for a single configuration.")
	@AllArgsConstructor
	@Getter @Setter
	public static class ConfigurationImportSummaryPart {

		/**
		 * The name of the configuration.
		 */
		@Schema(description = "The name of the configuration.", example = "anonymization")
		private String configurationName;

		/**
		 * Status of the configuration import.
		 */
		@Schema(description = "Status of the configuration import.")
		private ConfigurationImportPartStatus status;

		/**
		 * Null if the import was successful or ignored.
		 * Contains the error code if the import failed.
		 */
		@Nullable
		@Schema(description = "Null if the import was successful or ignored. Contains the error code if the import failed.")
		private String errorCode;

		/**
		 * Human-readable error message.
		 * Null if the import was successful or ignored.
		 */
		@Nullable
		@Schema(description = "Human-readable error message. Null if the import was successful or ignored.")
		private String errorMessage;

		/**
		 * Map containing the paths of values that failed validation and the validation errors.
		 * Null if no validation errors occurred.
		 */
		@Nullable
		@Schema(description = "Map containing the paths of values that failed validation and the validation errors. Null if no validation errors occurred.")
		private Map<String, Set<String>> validationErrors;
	}
}
