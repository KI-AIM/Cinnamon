package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service for selecting resources based on a selector string and project.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ResourceSelectorService {

	private final ConfigurationService configurationService;
	private final StepService stepService;
	private final DatabaseService databaseService;

	public ResourceSelectorService(@Lazy final ConfigurationService configurationService,
	                               final StepService stepService,
	                               final DatabaseService databaseService) {
		this.configurationService = configurationService;
		this.stepService = stepService;
		this.databaseService = databaseService;
	}

	/**
	 * Resolves the given argument.
	 * If the argument is a selector of the form {@code ${selector}}, the corresponding resource is returned.
	 * A default value may be appended after the selector, separated by a colon, e.g. {@code ${selector:defaultValue}}.
	 * The default value is returned if the selector does not resolve to a resource.
	 * If the argument is not a selector, it is returned unchanged.
	 *
	 * @param argument The argument to resolve.
	 * @param project The project used to resolve the selector.
	 * @return The resolved value.
	 */
	@Nullable
	public Object getValueFromSelector(final String argument, @Nullable final ProjectEntity project)
			throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException, BadStepNameException, InternalDataSetPersistenceException {

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

		final Object selectedResource = selectResource(selector, project);

		return selectedResource != null ? selectedResource : defaultValue;
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
	 * @param selector The selector string used to identify the resource.
	 * @param project  Project entity used to resolve the selector.
	 * @return The selected resource, or null if not found.
	 * @throws BadConfigurationNameException If the configuration name is invalid.
	 * @throws BadStateException             If the application state is invalid.
	 * @throws InternalIOException           A serialization error occurs.
	 * @throws InternalInvalidStateException If the project state is invalid.
	 * @throws BadStepNameException          If the step name is invalid.
	 */
	@Nullable
	public Object selectResource(final String selector, @Nullable final ProjectEntity project)
			throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException, BadStepNameException, InternalDataSetPersistenceException {
		final String[] parts = selector.split("\\.");

		return switch (parts[0]) {
			case "configuration" -> handleConfigurationSelector(parts, 1, project);
			case "original" -> handleOriginalSelector(parts, 1, project);
			case "pipeline" -> handlePipelineSelector(parts, 1, project);
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
			throws BadStepNameException, InternalInvalidStateException, InternalDataSetPersistenceException, BadStateException {
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


}
