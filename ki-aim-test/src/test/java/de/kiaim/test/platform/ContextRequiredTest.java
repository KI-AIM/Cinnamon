package de.kiaim.test.platform;

import de.kiaim.platform.PlatformApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public abstract class ContextRequiredTest {
}
