package de.kiaim.platform.service;

import de.kiaim.platform.exception.*;
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

	private final ProcessService processService;

	public StatisticsService(
			final ProcessService processService
	) {
		this.processService = processService;
	}

	/**
	 * Returns the statistics.
	 * @return The Statistics.
	 */
	@Transactional
	@Nullable
	public String getStatistics(final ProjectEntity project)
			throws InternalIOException, InternalDataSetPersistenceException, InternalRequestException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final BackgroundProcessEntity statisticsProcess = project.getOriginalData().getProcess();
		if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.FINISHED) {
			return statisticsProcess.getResultFiles().get("metrics.json").getLobString();
		} else {
			if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.NOT_STARTED ||
			    statisticsProcess.getExternalProcessStatus() == ProcessStatus.ERROR) {
				processService.startOrScheduleBackendProcess(project.getOriginalData().getProcess());
			}
			return null;
		}
	}
}
