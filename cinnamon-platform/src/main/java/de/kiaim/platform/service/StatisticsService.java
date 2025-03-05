package de.kiaim.platform.service;

import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.dto.DataSetSource;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.ProcessStatus;
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

	private final KiAimConfiguration kiAimConfiguration;

	private final DataSetService dataSetService;
	private final ProcessService processService;

	public StatisticsService(
			final KiAimConfiguration kiAimConfiguration,
			final DataSetService dataSetService,
			final ProcessService processService
	) {
		this.kiAimConfiguration = kiAimConfiguration;
		this.dataSetService = dataSetService;
		this.processService = processService;
	}

	/**
	 * Returns the statistics.
	 * @return The Statistics.
	 */
	@Transactional
	@Nullable
	public String getStatistics(final ProjectEntity project,
	                            final DataSetSource dataSetSource
	) throws BadStateException, BadDataSetIdException, InternalDataSetPersistenceException, InternalRequestException, InternalIOException, InternalInvalidStateException, InternalMissingHandlingException, BadStepNameException, InternalApplicationConfigurationException {
		final var dataset = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		if (!dataset.isStoredData())
		{
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final BackgroundProcessEntity statisticsProcess = dataset.getStatisticsProcess();
		if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.FINISHED) {
			return statisticsProcess.getResultFiles().get("metrics.json").getLobString();
		} else {
			if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.NOT_STARTED ||
			    statisticsProcess.getExternalProcessStatus() == ProcessStatus.ERROR) {
				statisticsProcess.setEndpoint(kiAimConfiguration.getStatisticsEndpoint());
				processService.startOrScheduleBackendProcess(statisticsProcess);
			}
			return null;
		}
	}
}
