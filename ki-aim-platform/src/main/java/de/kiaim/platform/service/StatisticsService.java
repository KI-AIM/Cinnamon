package de.kiaim.platform.service;

import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import org.apache.commons.io.IOUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for statistics.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StatisticsService {

	public static final String ID_PREFIX = "statistics_";

	private final DataSetService dataSetService;
	private final ProcessService processService;
	private final ProjectService projectService;

	public StatisticsService(
			final DataSetService dataSetService,
			final ProcessService processService,
			final ProjectService projectService
	) {
		this.processService = processService;
		this.projectService = projectService;
		this.dataSetService = dataSetService;
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
			return statisticsProcess.getResultFiles().get("metrics.yml").getLobString();
		} else {
			if (statisticsProcess.getExternalProcessStatus() == ProcessStatus.NOT_STARTED) {
				processService.startOrScheduleBackendProcess(project.getOriginalData().getProcess());
			}
			return null;
		}
	}

	/**
	 * TODO include in process service
	 * @return String containing the statistics.
	 */
	@Transactional
	public void finishStatistics(final String key, final MultipartFile metrics) throws InternalIOException {
		var ids = key.substring(ID_PREFIX.length());
		var id = Long.parseLong(ids);

		DataSetEntity d = dataSetService.getDataSetEntity(id);

		try {
			String data = IOUtils.toString(metrics.getInputStream(), StandardCharsets.UTF_8);
			d.getOriginalData().setStatistics(new LobWrapperEntity(data));
			projectService.saveProject(d.getOriginalData().getProject());
		} catch (IOException e) {
			throw new InternalIOException("", "", e);
		}
	}
}
