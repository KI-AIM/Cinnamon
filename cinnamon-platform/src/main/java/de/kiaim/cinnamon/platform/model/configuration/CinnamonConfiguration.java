package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Model for the steps defined in the application properties.
 */
@Component
@ConfigurationProperties(prefix = "cinnamon")
@Getter @Setter
public class CinnamonConfiguration {

	/**
	 * Configuration of the initial administrator account.
	 */
	@NestedConfigurationProperty
	private AdminConfiguration admin = new AdminConfiguration();

	/**
	 * Roles that are deleted when the users of a demo instance are reset.
	 * A user is only deleted if all their roles are contained in this set,
	 * so users with a role that is not listed here are kept.
	 */
	private Set<UserRole> demoInstanceDeletedRoles = new HashSet<>(Set.of(UserRole.ROLE_USER, UserRole.ROLE_API));

	@NestedConfigurationProperty
	private PasswordRequirementsConfiguration passwordRequirements = new PasswordRequirementsConfiguration();

	/**
	 * Timeout for establishing a connection to the cancel endpoint of external modules in milliseconds.
	 * Default is 5 seconds.
	 */
	private int requestsCancelConnectionTimeout = 5000;

	/**
	 * Timeout for receiving a response from the cancel endpoint of external modules in milliseconds.
	 * Default is 10 seconds.
	 */
	private int requestsCancelResponseTimeout = 10000;

	/**
	 * Map containing all steps.
	 */
	private Map<String, Job> steps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, Stage> stages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, ExternalConfiguration> externalConfiguration = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * Hosts that are running external server instances.
	 */
	private Map<String, ExternalHost> externalHost = new HashMap<>();

	private Map<String, ExternalServer> externalServer = new HashMap<>();

	private Map<Integer, ExternalEndpoint> externalServerEndpoints = new HashMap<>();

	private Integer statisticsEndpoint;

	@NestedConfigurationProperty
	private PipelineConfiguration pipeline = new PipelineConfiguration();
}
