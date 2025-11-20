package de.kiaim.cinnamon.test.platform;

import de.kiaim.cinnamon.platform.PlatformApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
@Import(TestTaskSchedulerConfig.class)
public abstract class ContextRequiredTest {
}
