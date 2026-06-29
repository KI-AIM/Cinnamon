package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.PipelineInformation;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.dto.WorkflowInformation;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.mapper.PipelineMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing workflows.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
@Log4j2
public class WorkflowService {

	// TODO set in admin interface
	public static final long DEFAULT_EXPIRATION_DAYS = 2L;

	private final PipelineMapper pipelineMapper;

	private final ConfigurationService configurationService;
	private final DatabaseService databaseService;
	private final ExportService exportService;
	private final ProcessService processService;
	private final ProjectService projectService;
	private final UserService userService;

	public WorkflowService(final PipelineMapper pipelineMapper,
	                       final ConfigurationService configurationService,
	                       final DatabaseService databaseService, ExportService exportService,
	                       final ProcessService processService,
	                       final ProjectService projectService,
	                       final UserService userService) {
		this.pipelineMapper = pipelineMapper;
		this.configurationService = configurationService;
		this.databaseService = databaseService;
		this.exportService = exportService;
		this.processService = processService;
		this.projectService = projectService;
		this.userService = userService;
	}

	/**
	 * Starts a new workflow.
	 *
	 * @param userEmail         The email of the user.
	 * @param dataFile          File containing the data to be anonymized.
	 * @param configurationFile File containing all required configurations.
	 * @return The ID of the started workflow.
	 * @throws BadAlgorithmException                     If one of the algorithms is not available.
	 * @throws BadArgumentException                      If the given configurations are invalid.
	 * @throws BadConfigurationFileException             If the configuration file is not a valid YAML file.
	 * @throws BadConfigurationNameException             If the configuration name used by the process is not valid.
	 * @throws BadDataConfigurationException             If the number of attributes does not match with the stored data configuration.
	 * @throws BadDatasetException                       If the data file could not be converted into a table.
	 * @throws BadDataSetIdException                     If the dataset has already been confirmed.
	 *                                                   If no DataConfiguration is associated with the given project.
	 * @throws BadFileException                          If the data file cannot be read.
	 * @throws BadStateException                         If a process of the stage is running.
	 *                                                   If no original dataset exists but is required by a process.
	 *                                                   If the file or any configuration are not available.
	 * @throws BadStepNameException                      If the given job is not part of the given stage.
	 * @throws BadUserException                          If the user is not found.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If a dataset table could not be deleted.
	 * @throws InternalErrorException                    If the ID could not be generated after 10 retries.
	 *                                                   If the dataset could not be exported due to an internal error.
	 *                                                   If the dataset could not be stored due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain a dataset.
	 * @throws InternalIOException                       If reading the data failed.
	 *                                                   If the request body could not be created.
	 * @throws InternalMissingHandlingException          If no processor for the file type of the file could be found.
	 *                                                   If no implementation exists for a valid configuration.
	 *                                                   If no processor exists for the selected data source type.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	@Transactional
	public UUID startWorkflow(final String userEmail, MultipartFile dataFile,
	                          final MultipartFile configurationFile)
			throws BadAlgorithmException, BadArgumentException, BadConfigurationFileException,
					       BadConfigurationNameException, BadDataConfigurationException, BadDatasetException,
					       BadDataSetIdException, BadFileException, BadStateException, BadStepNameException,
					       BadUserException, InternalApplicationConfigurationException,
					       InternalDataSetPersistenceException, InternalErrorException, InternalInvalidStateException,
					       InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);

		// 1. Create a new project
		final ProjectEntity project = projectService.createProject(user); // Name will be changed later
		project.setExpirationDate(
				new Timestamp(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DEFAULT_EXPIRATION_DAYS)));

		// 2. Configure the project
		final ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setAllowPartialImport(false);
		configurationService.importConfigurations(project, configurationFile, parameters);

		// 3. Import the data
		var retrievedDataFile = databaseService.retrieveFile(project);
		if (retrievedDataFile != null)
			dataFile = retrievedDataFile.getSecond();
		databaseService.storeFile(project, dataFile);
		databaseService.storeOriginalDataset(project);
		databaseService.confirmDataSet(project);

		// 4. Start the workflow
		final PipelineEntity pipeline = project.getPipelines().get(0);
		pipeline.setRunAllStages(true);
		processService.start(pipeline);

		// 5. Return the pipeline
		return project.getExternalId();
	}

	/**
	 * Returns the status of the workflow with the given ID.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @return The status of the workflow.
	 * @throws BadArgumentException          If the workflow ID is not a valid UUID.
	 * @throws BadUserException              If the user is not found.
	 * @throws BadProjectException          If the workflow is not found.
	 * @throws InternalInvalidStateException If fetching the status from the external module failed.
	 */
	@Transactional
	public WorkflowInformation getWorkflowStatus(final String userEmail, final String workflowId)
			throws BadArgumentException, BadUserException, BadProjectException, InternalInvalidStateException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);
		final ProjectEntity project = projectService.getProject(user, workflowId);
		final PipelineInformation pipeline = pipelineMapper.toDto(processService.getPipeline(project));
		return new WorkflowInformation(project.getExternalId().toString(), pipeline);
	}

	/**
	 * Returns the status of the workflow with the given ID.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @return The status of the workflow.
	 * @throws BadUserException              If the user is not found.
	 * @throws BadProjectException          If the workflow is not found.
	 * @throws InternalInvalidStateException If fetching the status from the external module failed.
	 */
	@Transactional(readOnly = true)
	public WorkflowInformation getWorkflowStatus(final String userEmail, final UUID workflowId)
			throws BadUserException, BadProjectException, InternalInvalidStateException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);
		final ProjectEntity project = projectService.getProject(user, workflowId);
		final PipelineInformation pipeline = pipelineMapper.toDto(processService.getPipeline(project));
		return new WorkflowInformation(project.getExternalId().toString(), pipeline);
	}

	/**
	 * Deletes the workflow with the given ID.
	 * The results are written to the response as a ZIP file.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @param response   The HTTP response.
	 * @return A response entity containing the ZIP file.
	 * @throws BadArgumentException                If the workflow ID is not a valid UUID.
	 * @throws BadConfigurationNameException       If the name of a configuration to export is unknown.
	 * @throws BadStateException                   If the project state is invalid for export.
	 * @throws BadStepNameException                If a resource from an unknown step is requested.
	 * @throws BadUserException                    If the user is not found.
	 * @throws BadProjectException                If the workflow is not found.
	 * @throws InternalDataSetPersistenceException If a dataset could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If a requested configuration is not valid, i.e., the validation during the import failed.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 *                                             If adding a resource to the ZIP file failed.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 */
	@Transactional
	public ResponseEntity<StreamingResponseBody> deleteWorkflow(final String userEmail, final String workflowId,
	                                                            final HttpServletResponse response)
			throws BadArgumentException, BadConfigurationNameException, BadStateException, BadStepNameException,
					       BadUserException, BadProjectException, InternalDataSetPersistenceException,
					       InternalInvalidStateException, InternalIOException, InternalMissingHandlingException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);
		final ProjectEntity project = projectService.getProject(user, workflowId);

		// Export the project
		final ResponseEntity<StreamingResponseBody> exportResponse = exportService.createZipFile(
				project, response, new ProjectExportParameter());

		// Delete the workflow
		projectService.deleteProject(user, project);

		return exportResponse;
	}

}
