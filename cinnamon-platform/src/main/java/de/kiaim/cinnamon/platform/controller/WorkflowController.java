package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.dto.PipelineInformation;
import de.kiaim.cinnamon.platform.model.dto.WorkflowRequest;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.mapper.PipelineMapper;
import de.kiaim.cinnamon.platform.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
@Tag(name = "/api/worklfow", description = "API for managing workflows.")
public class WorkflowController {

	private final PipelineMapper pipelineMapper;

	private final ConfigurationService configurationService;
	private final DatabaseService databaseService;
	private final ProcessService processService;
	private final ProjectService projectService;
	private final UserService userService;

	public WorkflowController(final PipelineMapper pipelineMapper,
	                          final ConfigurationService configurationService,
	                          final DatabaseService databaseService,
	                          final ProcessService processService,
	                          final ProjectService projectService,
	                          final UserService userService) {
		this.pipelineMapper = pipelineMapper;
		this.configurationService = configurationService;
		this.databaseService = databaseService;
		this.processService = processService;
		this.projectService = projectService;
		this.userService = userService;
	}

	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public PipelineInformation postNewWorkflow(
			@ParameterObject final WorkflowRequest workflowRequest,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// 1. Create a new project
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		if (projectService.hasProject(user)) {
			// TODO check if project is running
			projectService.deleteProject(user);
		}
		final ProjectEntity project = projectService.createProject(user);

		// 2. Configure the project
		final ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setAllowPartialImport(false);
		configurationService.importConfigurations(project, workflowRequest.getConfiguration(), parameters);

		// 3. Import the data
		databaseService.storeFile(project, workflowRequest.getData());
		databaseService.storeOriginalDataset(project);
		databaseService.confirmDataSet(project);

		// 4. Start the workflow
		final PipelineEntity pipeline = project.getPipelines().get(0);
		pipeline.setRunAllStages(true);
		processService.start(pipeline);

		// 5. Return the pipeline
		return pipelineMapper.toDto(processService.getPipeline(project));
	}

	@GetMapping(value = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public PipelineInformation getWorkflow(
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return pipelineMapper.toDto(processService.getPipeline(project));
	}
}
