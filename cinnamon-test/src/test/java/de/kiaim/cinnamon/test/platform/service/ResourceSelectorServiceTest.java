package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.platform.exception.BadStepNameException;
import de.kiaim.cinnamon.platform.exception.InternalInvalidStateException;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.DataProcessingEntity;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.service.ConfigurationService;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ResourceSelectorService;
import de.kiaim.cinnamon.platform.service.StepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ResourceSelectorService}, exercised exclusively through
 * {@link ResourceSelectorService#replaceSelectorsInString}.
 *
 * @author Daniel Preciado-Marquez
 */
public class ResourceSelectorServiceTest {

	private ConfigurationService configurationService;
	private StepService stepService;
	private DatabaseService databaseService;
	private ResourceSelectorService resourceSelectorService;

	@BeforeEach
	public void setup() {
		configurationService = mock(ConfigurationService.class);
		stepService = mock(StepService.class);
		databaseService = mock(DatabaseService.class);
		resourceSelectorService = new ResourceSelectorService(configurationService, stepService, databaseService);
	}

	//===========================
	//--- General replacement ---
	//===========================

	@Test
	public void noSelectors() throws Exception {
		final String result = replace("Hello world, nothing to replace here.", null, null, null);
		assertEquals("Hello world, nothing to replace here.", result);
	}

	@Test
	public void unterminatedSelectorIsLeftUnchanged() throws Exception {
		final String input = "prefix ${unterminated and the rest of the string";
		final String result = replace(input, null, null, null);
		assertEquals(input, result);
	}

	@Test
	public void unknownTopLevelSelectorIsLeftUnchanged() throws Exception {
		final String result = replace("${bogus.thing}", null, null, null);
		assertEquals("${bogus.thing}", result);
	}

	@Test
	public void unresolvedSelectorWithoutDefaultIsLeftUnchanged() throws Exception {
		// project is null, so the configuration selector cannot be resolved
		final String result = replace("${configuration.myConfig}", null, null, null);
		assertEquals("${configuration.myConfig}", result);
	}

	@Test
	public void unresolvedSelectorWithDefaultUsesDefault() throws Exception {
		final String result = replace("${configuration.myConfig:fallback}", null, null, null);
		assertEquals("fallback", result);
	}

	@Test
	public void resolvedSelectorWithDefaultIgnoresDefault() throws Exception {
		final ProjectEntity project = new ProjectEntity();
		final ConfigurationDTO configurationDto = configurationDto("CONFIG_VALUE");
		when(configurationService.loadConfiguration(eq("myConfig"), eq(project))).thenReturn(configurationDto);

		final String result = replace("${configuration.myConfig:fallback}", project, null, null);
		assertEquals("CONFIG_VALUE", result);
	}

	@Test
	public void multipleSelectorsAndPlainTextAreAllReplaced() throws Exception {
		final ProjectEntity project = new ProjectEntity();
		final ConfigurationDTO configurationDto = configurationDto("CONFIG_VALUE");
		when(configurationService.loadConfiguration(eq("myConfig"), eq(project))).thenReturn(configurationDto);

		final UserInvitationEntity invitation = new UserInvitationEntity();

		final String result = replace("Hi, config is ${configuration.myConfig} and link is ${invitation.url}!",
		                              project, invitation, "http://example.com/invite");

		assertEquals("Hi, config is CONFIG_VALUE and link is http://example.com/invite!", result);
	}

	//===========================
	//--- configuration.* ---
	//===========================

	@Test
	public void configurationSelectorResolvesConfiguration() throws Exception {
		final ProjectEntity project = new ProjectEntity();
		final ConfigurationDTO configurationDto = configurationDto("CONFIG_VALUE");
		when(configurationService.loadConfiguration(eq("myConfig"), eq(project))).thenReturn(configurationDto);

		final String result = replace("${configuration.myConfig}", project, null, null);
		assertEquals("CONFIG_VALUE", result);
	}

	//===========================
	//--- original.* ---
	//===========================

	@Test
	public void originalSelectorWithoutImportedDatasetIsLeftUnchanged() throws Exception {
		// No dataset has been imported yet, so no original.* selector can be resolved.
		final ProjectEntity project = new ProjectEntity();

		final String result = replace("${original.file}", project, null, null);
		assertEquals("${original.file}", result);
	}

	@Test
	public void originalFileSelectorResolvesFile() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();
		final String expected = project.getOriginalData().getFile().toString();

		final String result = replace("${original.file}", project, null, null);
		assertEquals(expected, result);
	}

	@Test
	public void originalDatasetSelectorResolvesWholeDataset() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();
		final String expected = project.getOriginalData().getDataSet().toString();

		final String result = replace("${original.dataset}", project, null, null);
		assertEquals(expected, result);
	}

	@Test
	public void originalDatasetNumberRowsSelectorResolvesRowCount() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();
		when(databaseService.getNumberRows(eq(project.getOriginalData().getDataSet()))).thenReturn(42);

		final String result = replace("${original.dataset.numberRows}", project, null, null);
		assertEquals("42", result);
	}

	@Test
	public void originalDatasetNumberHoldOutRowsSelectorResolvesRowCount() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();
		when(databaseService.getNumberHoldOutRows(eq(project.getOriginalData().getDataSet()))).thenReturn(7);

		final String result = replace("${original.dataset.numberHoldOutRows}", project, null, null);
		assertEquals("7", result);
	}

	@Test
	public void originalDatasetUnknownSubSelectorIsLeftUnchanged() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();

		final String result = replace("${original.dataset.bogus}", project, null, null);
		assertEquals("${original.dataset.bogus}", result);
	}

	@Test
	public void originalStatisticsSelectorResolvesStatisticsProcess() throws Exception {
		final ProjectEntity project = projectWithOriginalDataset();
		final String expected = project.getOriginalData().getDataSet().getStatisticsProcess().toString();

		final String result = replace("${original.statistics}", project, null, null);
		assertEquals(expected, result);
	}

	//===========================
	//--- pipeline.* ---
	//===========================

	@Test
	public void pipelineOtherSelectorResolvesProcess() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(false);
		mockStep(pipeline);

		final String result = replace("${pipeline.stageA.jobA.other}", pipeline.project(), null, null);
		assertEquals(pipeline.process().toString(), result);
	}

	@Test
	public void pipelineDatasetSelectorResolvesWholeDataset() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(true);
		mockStep(pipeline);

		final String result = replace("${pipeline.stageA.jobA.dataset}", pipeline.project(), null, null);
		assertEquals(pipeline.process().getDataSet().toString(), result);
	}

	@Test
	public void pipelineDatasetNumberRowsSelectorResolvesRowCount() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(true);
		mockStep(pipeline);
		when(databaseService.getNumberRows(eq(pipeline.process().getDataSet()))).thenReturn(13);

		final String result = replace("${pipeline.stageA.jobA.dataset.numberRows}", pipeline.project(), null, null);
		assertEquals("13", result);
	}

	@Test
	public void pipelineStatisticsSelectorResolvesStatisticsProcess() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(true);
		mockStep(pipeline);
		final String expected = pipeline.process().getDataSet().getStatisticsProcess().toString();

		final String result = replace("${pipeline.stageA.jobA.statistics}", pipeline.project(), null, null);
		assertEquals(expected, result);
	}

	@Test
	public void pipelineDatasetSelectorWithoutResultDatasetIsLeftUnchanged() throws Exception {
		// The process has not produced a dataset yet.
		final Pipeline pipeline = pipelineWithProcess(false);
		mockStep(pipeline);

		final String result = replace("${pipeline.stageA.jobA.dataset}", pipeline.project(), null, null);
		assertEquals("${pipeline.stageA.jobA.dataset}", result);
	}

	@Test
	public void pipelineSelectorWithMissingExecutionStepThrows() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(false);

		final Stage missingStage = new Stage();
		missingStage.setStageName("stageB");
		when(stepService.getStageConfiguration("stageB")).thenReturn(missingStage);

		assertThrows(InternalInvalidStateException.class,
		            () -> replace("${pipeline.stageB.jobA.other}", pipeline.project(), null, null));
	}

	@Test
	public void pipelineSelectorWithMissingProcessThrows() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(false);
		when(stepService.getStageConfiguration("stageA")).thenReturn(pipeline.stage());

		final Job missingJob = new Job();
		missingJob.setName("jobB");
		when(stepService.getStepConfiguration("jobB")).thenReturn(missingJob);

		assertThrows(InternalInvalidStateException.class,
		            () -> replace("${pipeline.stageA.jobB.other}", pipeline.project(), null, null));
	}

	@Test
	public void pipelineSelectorPropagatesBadStepNameException() throws Exception {
		final Pipeline pipeline = pipelineWithProcess(false);
		when(stepService.getStageConfiguration("bad")).thenThrow(new BadStepNameException("code", "message"));

		assertThrows(BadStepNameException.class,
		            () -> replace("${pipeline.bad.jobA.other}", pipeline.project(), null, null));
	}

	//===========================
	//--- invitation.* ---
	//===========================

	@Test
	public void invitationSelectorWithoutInvitationIsLeftUnchanged() throws Exception {
		final String result = replace("${invitation}", null, null, null);
		assertEquals("${invitation}", result);
	}

	@Test
	public void invitationSelectorResolvesWholeInvitation() throws Exception {
		final UserInvitationEntity invitation = new UserInvitationEntity();

		final String result = replace("${invitation}", null, invitation, null);
		assertEquals(invitation.toString(), result);
	}

	@Test
	public void invitationExpiresAtSelectorResolvesTimestamp() throws Exception {
		final UserInvitationEntity invitation = new UserInvitationEntity();
		final Timestamp expiresAt = Timestamp.valueOf("2026-01-01 12:00:00");
		invitation.setExpiresAt(expiresAt);

		final String result = replace("${invitation.expiresAt}", null, invitation, null);
		assertEquals(expiresAt.toString(), result);
	}

	@Test
	public void invitationUrlSelectorResolvesInvitationUrlParameter() throws Exception {
		final UserInvitationEntity invitation = new UserInvitationEntity();

		final String result = replace("${invitation.url}", null, invitation, "http://example.com/invite");
		assertEquals("http://example.com/invite", result);
	}

	@Test
	public void invitationUnknownSubSelectorIsLeftUnchanged() throws Exception {
		final UserInvitationEntity invitation = new UserInvitationEntity();

		final String result = replace("${invitation.bogus}", null, invitation, null);
		assertEquals("${invitation.bogus}", result);
	}

	//===========================
	//--- Helpers ---
	//===========================

	private String replace(final String input, final ProjectEntity project, final UserInvitationEntity invitation,
	                       final String invitationUrl) throws Exception {
		return resourceSelectorService.replaceSelectorsInString(input, project, invitation, invitationUrl);
	}

	private ConfigurationDTO configurationDto(final String value) {
		return new ConfigurationDTO() {
			@Override
			public String getKey() {
				return "myConfig";
			}

			@Override
			public String toString() {
				return value;
			}
		};
	}

	/**
	 * Creates a project with an imported original dataset, so {@code original.*} selectors can resolve.
	 */
	private ProjectEntity projectWithOriginalDataset() {
		final ProjectEntity project = new ProjectEntity();
		new DataSetEntity(project.getOriginalData());
		return project;
	}

	/**
	 * Creates a project with a pipeline containing a single stage "stageA" with a single job "jobA".
	 *
	 * @param withResultDataset If true, the process already produced a result dataset.
	 */
	private Pipeline pipelineWithProcess(final boolean withResultDataset) {
		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipelineEntity = new PipelineEntity();
		project.addPipeline(pipelineEntity);

		final Stage stage = new Stage();
		stage.setStageName("stageA");

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		pipelineEntity.addStage(stage, executionStep);

		final Job job = new Job();
		job.setName("jobA");

		final DataProcessingEntity process = new DataProcessingEntity();
		process.setJob(job);
		executionStep.addProcess(process);

		if (withResultDataset) {
			new DataSetEntity(process);
		}

		return new Pipeline(project, stage, job, process);
	}

	/**
	 * Stubs the step service to resolve the stage and job of the given pipeline.
	 */
	private void mockStep(final Pipeline pipeline) throws Exception {
		when(stepService.getStageConfiguration("stageA")).thenReturn(pipeline.stage());
		when(stepService.getStepConfiguration("jobA")).thenReturn(pipeline.job());
	}

	private record Pipeline(ProjectEntity project, Stage stage, Job job, DataProcessingEntity process) {
	}

}
