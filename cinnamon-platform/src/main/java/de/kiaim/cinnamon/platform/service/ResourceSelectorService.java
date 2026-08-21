package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.DateFormatPreset;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * Service for selecting resources based on a selector string and project.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ResourceSelectorService {

	/**
	 * Pattern used to render a {@link Timestamp} as an absolute, user-friendly date/time.
	 */
	private static final DateTimeFormatter ABSOLUTE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

	private final ConfigurationService configurationService;
	private final StepService stepService;
	private final DatabaseService databaseService;
	private final Clock clock;

	public ResourceSelectorService(@Lazy final ConfigurationService configurationService,
	                               final StepService stepService,
	                               final DatabaseService databaseService,
	                               final Clock clock) {
		this.configurationService = configurationService;
		this.stepService = stepService;
		this.databaseService = databaseService;
		this.clock = clock;
	}

	/**
	 * Resolves the given argument.
	 * If the argument is a selector of the form {@code ${selector}}, the corresponding resource is returned.
	 * <p>
	 * A user-friendly display format may be requested by appending {@code |format} to the selector, e.g.
	 * {@code ${selector|format}}. Which format keys are supported depends on the type of the resolved value;
	 * see {@link DateFormatPreset} for the formats supported for date/time values. If no format is requested,
	 * a type-appropriate default format is used where one exists (for date/time values, {@link DateFormatPreset#COMBINED}),
	 * otherwise the resource is returned unchanged.
	 * <p>
	 * A default value may be appended after the selector (and format), separated by a colon, e.g.
	 * {@code ${selector:defaultValue}} or {@code ${selector|format:defaultValue}}.
	 * The default value is returned if the selector does not resolve to a resource, or if an explicitly requested
	 * format is not supported for the resolved value's type.
	 * <p>
	 * If the argument is not a selector, it is returned unchanged.
	 *
	 * @param argument The argument to resolve.
	 * @param project The project used to resolve the selector.
	 * @return The resolved value.
	 */
	@Nullable
	public Object getValueFromSelector(
			final String argument,
			@Nullable final ProjectEntity project,
			@Nullable final UserInvitationEntity invitation,
			@Nullable final String invitationUrl
	) throws BadConfigurationNameException, BadStateException, BadStepNameException,
			         InternalDataSetPersistenceException, InternalIOException, InternalInvalidStateException {

		if (!argument.startsWith("${") || !argument.endsWith("}")) {
			return argument;
		}

		String selector = argument.substring(2, argument.length() - 1);
		String defaultValue = null;

		final int separatorIndex = selector.indexOf(':');
		if (separatorIndex != -1) {
			defaultValue = selector.substring(separatorIndex + 1);
			selector = selector.substring(0, separatorIndex);
		}

		String format = null;
		final int formatIndex = selector.indexOf('|');
		if (formatIndex != -1) {
			format = selector.substring(formatIndex + 1);
			selector = selector.substring(0, formatIndex);
		}

		final Object selectedResource = selectResource(selector, project, invitation, invitationUrl);
		if (selectedResource == null) {
			return defaultValue;
		}

		if (format != null) {
			final Object formatted = formatValue(selectedResource, format);
			return formatted != null ? formatted : defaultValue;
		}

		final Object defaultFormatted = formatValue(selectedResource, null);
		return defaultFormatted != null ? defaultFormatted : selectedResource;
	}

	/**
	 * Formats the given resolved selector value for user-friendly display, if a format is known for its type.
	 *
	 * @param value     The resolved, non-null selector value.
	 * @param formatKey The requested format key (case-insensitive), or {@code null} to use the type's default format.
	 * @return The formatted value, or {@code null} if no format is known for the value's type, or the requested
	 * format key is not supported.
	 */
	@Nullable
	private Object formatValue(final Object value, @Nullable final String formatKey) {
		if (!(value instanceof Timestamp timestamp)) {
			return null;
		}

		final DateFormatPreset preset;
		if (formatKey == null) {
			preset = DateFormatPreset.COMBINED;
		} else {
			try {
				preset = DateFormatPreset.valueOf(formatKey.toUpperCase());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		return formatTimestamp(timestamp, preset);
	}

	private String formatTimestamp(final Timestamp timestamp, final DateFormatPreset preset) {
		final Duration offset = Duration.between(clock.instant(), timestamp.toInstant());
		final String absolute = ABSOLUTE_DATE_TIME_FORMATTER.format(timestamp.toInstant().atZone(clock.getZone()));

		if (preset == DateFormatPreset.COMBINED) {
			return formatRelativeDuration(offset) + " (" + absolute + ")";
		}

		final Duration threshold = preset.getRelativeThreshold();
		return threshold != null && offset.abs().compareTo(threshold) < 0 ? formatRelativeDuration(offset) : absolute;
	}

	/**
	 * Renders the given offset from now as relative wording, e.g. "in 3 days" for a positive offset, or
	 * "3 days ago" for a negative one.
	 *
	 * @param offset The offset from now; positive for a point in the future, negative for a point in the past.
	 */
	private static String formatRelativeDuration(final Duration offset) {
		final boolean past = offset.isNegative();
		final Duration magnitude = offset.abs();

		final long minutes = magnitude.toMinutes();
		if (minutes < 1) {
			return "just now";
		}
		if (minutes < 60) {
			return formatRelative(minutes, "minute", past);
		}

		final long hours = magnitude.toHours();
		if (hours < 24) {
			return formatRelative(hours, "hour", past);
		}

		final long days = magnitude.toDays();
		return formatRelative(days, "day", past);
	}

	private static String formatRelative(final long amount, final String unit, final boolean past) {
		final String plural = unit + (amount == 1 ? "" : "s");
		return past ? amount + " " + plural + " ago" : "in " + amount + " " + plural;
	}

	/**
	 * Replaces all selectors in the given input string with their corresponding values.
	 *
	 * @param input The input string containing selectors to be replaced.
	 * @param project The project entity used to resolve the selectors.
	 * @param invitation The user invitation entity used to resolve invitation-related selectors.
	 * @param invitationUrl The URL associated with the invitation, used for resolving invitation-related selectors.
	 * @return The input string with all selectors replaced by their corresponding values.
	 * @throws BadConfigurationNameException       If the configuration name is invalid.
	 * @throws BadStateException                   If the application state is invalid.
	 * @throws BadStepNameException                If the step name is invalid.
	 * @throws InternalDataSetPersistenceException If there is an error accessing the dataset.
	 * @throws InternalIOException                 A serialization error occurs.
	 * @throws InternalInvalidStateException       If the project state is invalid.
	 */
	public String replaceSelectorsInString(
			final String input,
			@Nullable final ProjectEntity project,
			@Nullable final UserInvitationEntity invitation,
			@Nullable final String invitationUrl
	) throws BadConfigurationNameException, BadStateException, BadStepNameException,
			         InternalDataSetPersistenceException, InternalIOException, InternalInvalidStateException {
		StringBuilder result = new StringBuilder();
		int lastIndex = 0;

		while (true) {
			int startIndex = input.indexOf("${", lastIndex);
			if (startIndex == -1) {
				result.append(input.substring(lastIndex));
				break;
			}

			int endIndex = input.indexOf("}", startIndex);
			if (endIndex == -1) {
				result.append(input.substring(lastIndex));
				break;
			}

			result.append(input, lastIndex, startIndex);

			String selector = input.substring(startIndex, endIndex + 1);
			Object selectedResource = getValueFromSelector(selector, project, invitation, invitationUrl);

			if (selectedResource != null) {
				result.append(selectedResource);
			} else {
				result.append(selector);
			}

			lastIndex = endIndex + 1;
		}

		return result.toString();
	}

	/**
	 * Selects a resource based on the given selector string and project.
	 * The return type depends on the selector and can be one of the following:
	 * <ul>
	 *     <li>{@link ConfigurationDTO} for configuration resources</li>
	 *     <li>{@link DataSetEntity} for dataset resources</li>
	 *     <li>{@link FileEntity} for file resources</li>
	 *     <li>{@link LobWrapperEntity} for LOB resources</li>
	 *     <li>{@link BackgroundProcessEntity} for statistics resources</li>
	 *     <li>{@link ExternalProcessEntity} for other resources</li>
	 * </ul>
	 *
	 * @param selector      The selector string used to identify the resource.
	 * @param project       Project entity used to resolve the selector.
	 * @param invitation    The user invitation entity used to resolve invitation-related selectors.
	 * @param invitationUrl The URL associated with the invitation, used for resolving invitation-related selectors.
	 * @return The selected resource, or null if not found.
	 * @throws BadConfigurationNameException       If the configuration name is invalid.
	 * @throws BadStateException                   If the application state is invalid.
	 * @throws InternalIOException                 A serialization error occurs.
	 * @throws InternalInvalidStateException       If the project state is invalid.
	 * @throws BadStepNameException                If the step name is invalid.
	 * @throws InternalDataSetPersistenceException If there is an error accessing the dataset.
	 */
	@Nullable
	public Object selectResource(final String selector, @Nullable final ProjectEntity project,
	                             @Nullable final UserInvitationEntity invitation, @Nullable final String invitationUrl)
			throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException, BadStepNameException, InternalDataSetPersistenceException {
		final String[] parts = selector.split("\\.");

		return switch (parts[0]) {
			case "configuration" -> handleConfigurationSelector(parts, 1, project);
			case "original" -> handleOriginalSelector(parts, 1, project);
			case "pipeline" -> handlePipelineSelector(parts, 1, project);
			case "invitation" -> handleInvitation(parts, 1, invitation, invitationUrl);
			default -> null;
		};
	}

	@Nullable
	private ConfigurationDTO handleConfigurationSelector(final String[] parts, final int nextPart,
	                                                     @Nullable final ProjectEntity project)
			throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException {
		if (project == null)
			return null;

		final String configName = parts[nextPart];
		return configurationService.loadConfiguration(configName, project);
	}

	@Nullable
	private Object handleOriginalSelector(final String[] parts, final int nextPart,
	                                      @Nullable final ProjectEntity project)
			throws InternalDataSetPersistenceException {
		if (project == null)
			return null;

		final OriginalDataEntity originalData = project.getOriginalData();
		final DataSetEntity dataSetEntity = originalData.getDataSet();
		if (dataSetEntity == null)
			return null;

		return switch (parts[nextPart]) {
			case "file" -> handleFileSelector(parts, originalData.getFile());
			case "dataset" -> handleDatasetSelector(parts, nextPart + 1, dataSetEntity);
			case "statistics" -> handleStatisticsSelector(parts, dataSetEntity.getStatisticsProcess());
			default -> null;
		};
	}

	@Nullable
	private Object handlePipelineSelector(final String[] parts, final int nextPart,
	                                      @Nullable final ProjectEntity project)
			throws BadStepNameException, InternalInvalidStateException, InternalDataSetPersistenceException {
		if (project == null)
			return null;

		final PipelineEntity pipeline = project.getPipelines().get(0);
		final Stage stage = stepService.getStageConfiguration(parts[nextPart]);
		final ExecutionStepEntity executionStep = pipeline.getStageByStep(stage);

		if (executionStep == null) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_STAGE,
			                                        "Execution step not found for stage: " + stage.getStageName());
		}

		final Job job = stepService.getStepConfiguration(parts[nextPart + 1]);
		final ExternalProcessEntity externalProcess = executionStep.getProcess(job).orElseThrow(
				() -> new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
				                                        "External process not found for job: " + job.getName()));

		if (parts[nextPart + 2].equals("other")) {
			return externalProcess;
		}

		if (externalProcess instanceof DataProcessingEntity dataProcessing) {
			if (dataProcessing.getDataSet() != null) {
				return switch (parts[nextPart + 2]) {
					case "dataset" -> handleDatasetSelector(parts, nextPart + 3, dataProcessing.getDataSet());
					case "statistics" -> handleStatisticsSelector(parts, dataProcessing.getDataSet().getStatisticsProcess());
					default -> null;
				};
			}
		}

		return null;
	}

	@Nullable
	private FileEntity handleFileSelector(final String[] parts, @Nullable final FileEntity fileEntity) {
		return fileEntity;
	}

	@Nullable
	private Object handleDatasetSelector(final String[] parts, final int nextPart, final DataSetEntity dataSetEntity)
			throws InternalDataSetPersistenceException {
		if (parts.length <= nextPart) {
			return dataSetEntity;
		}

		return switch (parts[nextPart]) {
			case "numberRows" ->  databaseService.getNumberRows(dataSetEntity);
			case "numberHoldOutRows" -> databaseService.getNumberHoldOutRows(dataSetEntity);
			default -> null;
		};
	}

	private BackgroundProcessEntity handleStatisticsSelector(final String[] parts,
	                                                         final BackgroundProcessEntity statistics) {
		return statistics;
	}

	private Object handleInvitation(final String[] parts, final int nextPart,
	                                @Nullable final UserInvitationEntity invitation,
	                                @Nullable final String invitationUrl) {
		if (invitation == null) {
			return null;
		}

		if (parts.length <= nextPart) {
			return invitation;
		}

		return switch (parts[nextPart]) {
			case "expiresAt" -> invitation.getExpiresAt();
			case "url" -> invitationUrl;
			default -> null;
		};
	}

}
