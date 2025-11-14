package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.DatasetStatistics;
import de.kiaim.cinnamon.platform.model.dto.StatisticsResponse;
import de.kiaim.cinnamon.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.DataSetSource;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for statistics.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StatisticsService {

	private final CinnamonConfiguration cinnamonConfiguration;

	private final DataSetService dataSetService;
	private final ProcessService processService;
	private final ProjectService projectService;

	public StatisticsService(
			final CinnamonConfiguration cinnamonConfiguration,
			final DataSetService dataSetService,
			final ProcessService processService,
			final ProjectService projectService
	) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.dataSetService = dataSetService;
		this.processService = processService;
		this.projectService = projectService;
	}

	/**
	 * Calculates statistics for the given data set source.
	 * If statistics have already been calculated, return the existing statistics.
	 *
	 * @return The Statistics.
	 */
	@Transactional
	public StatisticsResponse getStatistics(final ProjectEntity project, final DataSetSource dataSetSource,
	                                        final String statisticsKey
	) throws BadStateException, BadDataSetIdException, BadDatasetStatisticsKeyException,
			         InternalDataSetPersistenceException, InternalRequestException, InternalIOException,
			         InternalInvalidStateException, InternalMissingHandlingException, BadStepNameException, InternalApplicationConfigurationException {
		final var dataset = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		if (!dataset.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final BackgroundProcessEntity statisticsProcess = getOrCreateStatisticsProcess(dataset, statisticsKey);
		if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.FINISHED) {
			return new StatisticsResponse(ProcessStatus.FINISHED,
			                              statisticsProcess.getResultFiles().get("metrics.json").getLobString());
		} else {
			if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.NOT_STARTED ||
			    statisticsProcess.getExternalProcessStatus() == ProcessStatus.ERROR ||
			    statisticsProcess.getExternalProcessStatus() == ProcessStatus.CANCELED ||
			    statisticsProcess.getExternalProcessStatus() == ProcessStatus.OUTDATED) {

				try {
					statisticsProcess.setEndpoint(cinnamonConfiguration.getStatisticsEndpoint());
					processService.startOrScheduleBackendProcess(statisticsProcess);
				} catch (Exception e) {
					processService.setProcessError(statisticsProcess, e.getMessage());
					throw e;
				} finally {
					projectService.saveProject(project);
				}

			}
			return new StatisticsResponse(statisticsProcess.getExternalProcessStatus(), null);
		}
	}

	public StatisticsResponse cancelStatistics(final ProjectEntity project, final DataSetSource dataSetSource,
	                                           final String statisticsKey)
			throws BadDataSetIdException, BadDatasetStatisticsKeyException, BadStateException, BadStepNameException,
					       InternalApplicationConfigurationException, InternalInvalidStateException,
					       InternalMissingHandlingException {
		final var dataset = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		if (!dataset.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final BackgroundProcessEntity statisticsProcess = getStatisticsProcessOrThrow(dataset, statisticsKey);
		processService.cancelProcess(statisticsProcess);

		return new StatisticsResponse(statisticsProcess.getExternalProcessStatus(), null);
	}

	/**
	 * Returns the process for calculating the statistics for the given key.
	 *
	 * @param dataset The dataset.
	 * @param statisticsKey The key identifying the statistics.
	 * @return The process.
	 * @throws BadDatasetStatisticsKeyException If there are no statistics defined by the key or the no process for these statistics exists.
	 */
	private BackgroundProcessEntity getStatisticsProcessOrThrow(final DataSetEntity dataset,
	                                                            final String statisticsKey) throws BadDatasetStatisticsKeyException {
		var process = getStatisticsProcess(dataset, statisticsKey);
		if (process == null) {
			throw new BadDatasetStatisticsKeyException(BadDatasetStatisticsKeyException.NOT_FOUND,
			                                           "No statistics are available for the key '" + statisticsKey +
			                                           "' of the dataset" + dataset.getId());
		}
		return process;
	}

	/**
	 * Returns the process for calculating the statistics for the given key.
	 * The return value is null if the key is defined, but no process exists.
	 *
	 * @param dataset The dataset.
	 * @param statisticsKey The key identifying the statistics.
	 * @return The process or null.
	 * @throws BadDatasetStatisticsKeyException If there are no statistics defined by the key.
	 */
	@Nullable
	private BackgroundProcessEntity getStatisticsProcess(final DataSetEntity dataset,
	                                                     final String statisticsKey) throws BadDatasetStatisticsKeyException {
		final var datasetStatistics = getDatasetStatistics(statisticsKey);
		return dataset.getStatisticsProcess(datasetStatistics.getEndpoint());
	}

	/**
	 * Creates a new process or returns an existing one for calculating the statistics defined by the given key for the given dataset.
	 *
	 * @param dataset The dataset to calculate the statistics for.
	 * @param statisticsKey The key defining the statistics to calculate.
	 * @return The process.
	 * @throws BadDatasetStatisticsKeyException If there are no statistics defined by the key.
	 */
	private BackgroundProcessEntity getOrCreateStatisticsProcess(final DataSetEntity dataset,
	                                                             final String statisticsKey) throws BadDatasetStatisticsKeyException {
		var process = getStatisticsProcess(dataset, statisticsKey);

		if (process == null) {
			var datasetStatistics = getDatasetStatistics(statisticsKey);
			process = new BackgroundProcessEntity(dataset, datasetStatistics.getEndpoint());
			dataset.addStatisticsProcess(process);
		}

		return process;
	}

	/**
	 * Returns the definition of the dataset statistics for the given key.
	 *
	 * @param statisticsKey The key.
	 * @return The statistics definition.
	 * @throws BadDatasetStatisticsKeyException If there are no statistics defined by the key.
	 */
	private DatasetStatistics getDatasetStatistics(final String statisticsKey) throws BadDatasetStatisticsKeyException {
		for (final var datasetStatistic : cinnamonConfiguration.getDatasetStatistics()) {
			if (datasetStatistic.getKey().equals(statisticsKey)) {
				return datasetStatistic;
			}
		}

		throw new BadDatasetStatisticsKeyException(BadDatasetStatisticsKeyException.NOT_DEFINED,
		                                           "No statistics are configured for the key '" + statisticsKey + "'");
	}
}
