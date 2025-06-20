package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Model for the steps defined in the application properties.
 */
@Component
@ConfigurationProperties(prefix = "cinnamon")
@Getter @Setter
public class CinnamonConfiguration {

	@NestedConfigurationProperty
	private PasswordRequirementsConfiguration passwordRequirements = new PasswordRequirementsConfiguration();

	/**
	 * Map containing all steps.
	 */
	private Map<String, Job> steps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, Stage> stages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, ExternalConfiguration> externalConfiguration = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, ExternalServer> externalServer = new HashMap<>();

	private Map<Integer, ExternalEndpoint> externalServerEndpoints = new HashMap<>();

	private Integer statisticsEndpoint;

	@NestedConfigurationProperty
	private PipelineConfiguration pipeline = new PipelineConfiguration();
}
