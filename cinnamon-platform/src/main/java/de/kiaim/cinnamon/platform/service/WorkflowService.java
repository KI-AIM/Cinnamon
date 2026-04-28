package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.PipelineInformation;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.dto.WorkflowInformation;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.entity.WorkflowEntity;
import de.kiaim.cinnamon.platform.model.mapper.PipelineMapper;
import de.kiaim.cinnamon.platform.repository.WorkflowRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.sql.Timestamp;
import java.util.List;
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

	public static final long DEFAULT_EXPIRATION_DAYS = 2L;
	public static final int GEN_WORKFLOW_ID_MAX_RETRIES = 10;

	private final WorkflowRepository workflowRepository;

	private final PipelineMapper pipelineMapper;

	private final ConfigurationService configurationService;
	private final DatabaseService databaseService;
	private final ExportService exportService;
	private final ProcessService processService;
	private final ProjectService projectService;
	private final UserService userService;

	public WorkflowService(final WorkflowRepository workflowRepository,
	                       final PipelineMapper pipelineMapper,
	                       final ConfigurationService configurationService,
	                       final DatabaseService databaseService, ExportService exportService,
	                       final ProcessService processService,
	                       final ProjectService projectService,
	                       final UserService userService) {
		this.workflowRepository = workflowRepository;
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
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	@Transactional
	public UUID startWorkflow(final String userEmail, final MultipartFile dataFile,
	                          final MultipartFile configurationFile)
			throws BadAlgorithmException, BadArgumentException, BadConfigurationFileException,
					       BadConfigurationNameException, BadDataConfigurationException, BadDatasetException,
					       BadDataSetIdException, BadFileException, BadStateException, BadStepNameException,
					       BadUserException, InternalApplicationConfigurationException,
					       InternalDataSetPersistenceException, InternalErrorException, InternalInvalidStateException,
					       InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);

		// 1. Create a new workflow
		final WorkflowEntity workflow = new WorkflowEntity();
		workflow.setWorkflowId(generateUUID());
		workflow.setExpirationDate(
				new Timestamp(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DEFAULT_EXPIRATION_DAYS)));
		final ProjectEntity project = projectService.createProject(System.currentTimeMillis());
		workflow.setProject(project);
		user.addWorkflow(workflow);
		log.debug("Created new workflow with ID {} for user {}", workflow.getWorkflowId(), userEmail);

		// 2. Configure the project
		final ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setAllowPartialImport(false);
		configurationService.importConfigurations(project, configurationFile, parameters);

		// 3. Import the data
		databaseService.storeFile(project, dataFile);
		databaseService.storeOriginalDataset(project);
		databaseService.confirmDataSet(project);

		// 4. Start the workflow
		final PipelineEntity pipeline = project.getPipelines().get(0);
		pipeline.setRunAllStages(true);
		processService.start(pipeline);

		// 5. Return the pipeline
		return workflow.getWorkflowId();
	}

	/**
	 * Returns the status of the workflow with the given ID.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @return The status of the workflow.
	 * @throws BadArgumentException          If the workflow ID is not a valid UUID.
	 * @throws BadUserException              If the user is not found.
	 * @throws BadWorkflowException          If the workflow is not found.
	 * @throws InternalInvalidStateException If fetching the status from the external module failed.
	 */
	@Transactional
	public WorkflowInformation getWorkflowStatus(final String userEmail, final String workflowId)
			throws BadArgumentException, BadUserException, BadWorkflowException, InternalInvalidStateException {
		final WorkflowEntity workflow = getWorkflowEntity(userEmail, workflowId);
		final PipelineInformation pipeline = pipelineMapper.toDto(processService.getPipeline(workflow.getProject()));
		return new WorkflowInformation(workflow.getWorkflowId().toString(), pipeline);
	}

	/**
	 * Returns the status of the workflow with the given ID.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @return The status of the workflow.
	 * @throws BadUserException              If the user is not found.
	 * @throws BadWorkflowException          If the workflow is not found.
	 * @throws InternalInvalidStateException If fetching the status from the external module failed.
	 */
	@Transactional
	public WorkflowInformation getWorkflowStatus(final String userEmail, final UUID workflowId)
			throws BadUserException, BadWorkflowException, InternalInvalidStateException {
		final WorkflowEntity workflow = getWorkflowEntity(userEmail, workflowId);
		final PipelineInformation pipeline = pipelineMapper.toDto(processService.getPipeline(workflow.getProject()));
		return new WorkflowInformation(workflow.getWorkflowId().toString(), pipeline);
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
	 * @throws BadWorkflowException                If the workflow is not found.
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
					       BadUserException, BadWorkflowException, InternalDataSetPersistenceException,
					       InternalInvalidStateException, InternalIOException, InternalMissingHandlingException {
		final WorkflowEntity workflow = getWorkflowEntity(userEmail, workflowId);

		// Export the project
		final ResponseEntity<StreamingResponseBody> exportResponse = exportService.createZipFile(
				workflow.getProject(), response, new ProjectExportParameter());

		// Delete the workflow
		deleteWorkflow(workflow);

		return exportResponse;
	}

	/**
	 * Deletes the given workflow.
	 *
	 * @param workflow The workflow to delete.
	 * @throws InternalDataSetPersistenceException If the dataset could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If the process to be canceled has no server instance assigned.
	 */
	@Transactional
	public void deleteWorkflow(final WorkflowEntity workflow)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		final UserEntity user = workflow.getUser();
		projectService.resetEntireProject(workflow.getProject());
		user.removeWorkflow(workflow);
		log.debug("Deleted workflow with ID {} for user {}", workflow.getWorkflowId(), user.getEmail());
	}

	/**
	 * Returns all workflows that have expired.
	 *
	 * @return List of expired workflows.
	 */
	public List<WorkflowEntity> getExpiredWorkflows() {
		final Timestamp expirationDate = new Timestamp(System.currentTimeMillis());
		return workflowRepository.findAllByExpirationDateBefore(expirationDate);
	}

	/**
	 * Returns the workflow with the given ID owned by the user with the given email.
	 *
	 * @param userEmail  The email of the user owning the workflow.
	 * @param workflowId The ID of the workflow.
	 * @return The workflow entity.
	 * @throws BadArgumentException If the workflow ID is not a valid UUID.
	 * @throws BadUserException     If the user is not found.
	 * @throws BadWorkflowException If the workflow is not found.
	 */
	private WorkflowEntity getWorkflowEntity(final String userEmail, final String workflowId)
			throws BadArgumentException, BadUserException, BadWorkflowException {
		UUID workflowIdAsUUID;

		try {
			workflowIdAsUUID = UUID.fromString(workflowId);
		} catch (IllegalArgumentException e) {
			throw new BadArgumentException(BadArgumentException.INVALID_WORKFLOW_ID, "Invalid workflow ID format");
		}


		return getWorkflowEntity(userEmail, workflowIdAsUUID);
	}

	/**
	 * See {@link #getWorkflowEntity(String, String)}.
	 */
	private WorkflowEntity getWorkflowEntity(final String userEmail, final UUID workflowId)
			throws BadUserException, BadWorkflowException {
		final UserEntity user = userService.getUserByEmailOrThrow(userEmail);
		final WorkflowEntity workflow = user.getWorkflow(workflowId);
		if (workflow == null) {
			throw new BadWorkflowException(BadWorkflowException.NOT_FOUND,
			                               "Workflow with ID " + workflowId + " not found");
		}

		return workflow;
	}

	/**
	 * Generates a unique workflow ID.
	 *
	 * @return The generated workflow ID.
	 * @throws InternalErrorException If the ID could not be generated after 10 retries.
	 */
	private UUID generateUUID() throws InternalErrorException {
		UUID uuid;
		for (int i = 0; i < GEN_WORKFLOW_ID_MAX_RETRIES; i++) {
			uuid = UUID.randomUUID();

			if (workflowRepository.countByWorkflowId(uuid) == 0) {
				return uuid;
			}
		}

		throw new InternalErrorException(InternalErrorException.GEN_WORKFLOW_ID_MAX_RETRIES,
		                                 "Failed to generate a unique workflow ID after " +
		                                 GEN_WORKFLOW_ID_MAX_RETRIES + " retries! Please try again later.");
	}

}
