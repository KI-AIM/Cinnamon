package de.kiaim.cinnamon.test.platform;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.model.enumeration.StageStatus;
import de.kiaim.cinnamon.platform.PlatformApplication;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import de.kiaim.cinnamon.test.util.TestDatabaseExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

@ExtendWith(TestDatabaseExtension.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
@Import(TestTaskSchedulerConfig.class)
public abstract class ContextRequiredTest {

	@Autowired private ExternalConfigurationService externalConfigurationService;

	protected TestDatabaseExtension.TestDatabase activeDatabase;

	@AfterEach
	public void tearDown() {
		externalConfigurationService.clearCaches();
	}

	protected ProjectEntity createProject(final Stage stage, final ProcessStatus status) {
		final ExternalProcessEntity externalProcess = new DataProcessingEntity();
		externalProcess.setExternalProcessStatus(status);
		final var job = stage.getJobList().stream()
		                    .filter(candidate -> candidate.getName().equals("anonymization"))
		                    .findFirst()
		                    .get(); // We are fine with this failing if the anonymization is not there
		externalProcess.setJob(job);
		externalProcess.setUuid(UUID.randomUUID());

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setStatus(status == ProcessStatus.RUNNING ? StageStatus.RUNNING : StageStatus.FINISHED);
		executionStep.addProcess(externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		pipeline.addStage(stage, executionStep);

		if (status == ProcessStatus.RUNNING) {
			executionStep.setCurrentProcessIndex(0);
			externalProcess.setServerInstance(job.getServer().getName() + ".0");
		} else if (status == ProcessStatus.FINISHED) {
			externalProcess.setStatus("FINISHED");
			externalProcess.getResultFiles().put("data", new LobWrapperEntity());
		}

		return project;
	}


}
