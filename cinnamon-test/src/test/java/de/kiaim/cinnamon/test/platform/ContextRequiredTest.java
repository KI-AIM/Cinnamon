package de.kiaim.cinnamon.test.platform;

import de.kiaim.cinnamon.platform.PlatformApplication;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
@Import(TestTaskSchedulerConfig.class)
public abstract class ContextRequiredTest {

	@Autowired private ExternalConfigurationService externalConfigurationService;

	@AfterEach
	public void tearDown() {
		externalConfigurationService.clearCaches();
	}

}
