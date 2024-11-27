package de.kiaim.platform.config;

import de.kiaim.platform.model.enumeration.Step;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Model for the steps defined in the application properties.
 */
@Component
@ConfigurationProperties(prefix = "ki-aim")
@Getter @Setter
public class KiAimConfiguration {
	/**
	 * Map containing all steps.
	 */
	private Map<Step, StepConfiguration> steps = new HashMap<>();

	private Map<Step, StageConfiguration> stages = new HashMap<>();

	private PipelineConfiguration pipeline = new PipelineConfiguration();
}
