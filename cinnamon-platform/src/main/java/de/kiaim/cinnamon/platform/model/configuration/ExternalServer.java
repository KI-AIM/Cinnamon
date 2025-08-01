package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.helper.KiAimConfigurationPostProcessor;
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
	 * See {@link #getMinUp()}
	 */
	@Nullable
	private Integer minUp = null;

	/**
	 * List of instances for the external server.
	 */
	private Map<String, ExternalServerInstance> instances = new HashMap<>();

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Name of the server. Is automatically set at {@link KiAimConfigurationPostProcessor#init()}.
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
