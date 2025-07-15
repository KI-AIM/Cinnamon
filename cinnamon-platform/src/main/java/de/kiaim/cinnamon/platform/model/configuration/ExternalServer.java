package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.helper.CinnamonConfigurationPostProcessor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter @Setter
public class ExternalServer {

	/**
	 * Host name for sending callbacks from instances of this external server to the platform.
	 * Default value for all instances.
	 * Can be overwritten in the instance configuration.
	 */
	private String callbackHost;

	/**
	 * Maximum number of processes that are allowed to run in parallel for each instance.
	 * Negative numbers allow an unlimited number of processes.
	 * Can be overwritten in the instance configuration.
	 * <p>
	 * The default is -1.
	 */
	private int maxParallelProcess = -1;

	/**
	 * Endpoint used for health checks.
	 */
	private String healthEndpoint = "";

	/**
	 * Timeout in milliseconds for the health endpoint.
	 * If the request timeouts the instance is considered DOWN.
	 * Can be overwritten in the instance configuration.
	 * <p>
	 * The default value is 10 s.
	 */
	private int healthTimeout = 10_000;

	/**
	 * See {@link #getMinUp()}
	 */
	@Nullable
	private Integer minUp = null;

	/**
	 * Default host port used for all instances.
	 * <p>
	 * The default value is 80.
	 */
	private int instanceHostPort = 80;

	/**
	 * List of instances for the external server.
	 */
	private Map<String, ExternalServerInstance> instances = new HashMap<>();

	@Nullable
	private String reportEndpoint = null;

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Name of the server. Is automatically set at {@link CinnamonConfigurationPostProcessor#init()}.
	 */
	private String name;

	/**
	 * Endpoints of the server.
	 */
	private List<ExternalEndpoint> endpoints = new ArrayList<>();

	//===============
	//--- Getters ---
	//===============

	/**
	 * The minimum number of instances that have to be UP.
	 * If the value is smaller than the number of instances, health checks are performed before starting processes.
	 * It's also used for determining the status of the external server in health checks.
	 * <p>
	 * If no value is provided, the number of instances is used.
	 */
	public int getMinUp() {
		return minUp == null ? instances.size() : minUp;
	}
}
