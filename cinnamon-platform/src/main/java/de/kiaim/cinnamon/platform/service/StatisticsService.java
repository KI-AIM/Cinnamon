package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.StatisticsResponse;
import de.kiaim.cinnamon.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.DataSetSource;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
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
	public StatisticsResponse getStatistics(final ProjectEntity project,
	                                        final DataSetSource dataSetSource
	) throws BadStateException, BadDataSetIdException, InternalDataSetPersistenceException, InternalRequestException, InternalIOException, InternalInvalidStateException, InternalMissingHandlingException, BadStepNameException, InternalApplicationConfigurationException {
		final var dataset = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		if (!dataset.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final BackgroundProcessEntity statisticsProcess = dataset.getStatisticsProcess();
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

	public StatisticsResponse cancelStatistics(final ProjectEntity project, final DataSetSource dataSetSource) throws BadStateException, BadStepNameException, InternalApplicationConfigurationException, BadDataSetIdException, InternalInvalidStateException, InternalMissingHandlingException {
		final var dataset = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		if (!dataset.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final BackgroundProcessEntity statisticsProcess = dataset.getStatisticsProcess();
		processService.cancelProcess(statisticsProcess);

		return new StatisticsResponse(statisticsProcess.getExternalProcessStatus(), null);
	}
}
