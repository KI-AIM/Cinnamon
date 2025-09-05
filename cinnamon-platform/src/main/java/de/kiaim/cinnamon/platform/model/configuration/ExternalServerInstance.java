package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Defines one instance of an external server.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class ExternalServerInstance {

	/**
	 * String used for separating the server name and the instance name in the ID.
	 */
	public static final String ID_SEPARATOR = ".";

	/**
	 * See {@link #getCallbackHost()}.
	 */
	@Nullable
	private String callbackHost = null;

	/**
	 * See {@link #getHealthTimeout()}.
	 * <p>
	 * The default value is {@code null}.
	 */
	@Nullable
	private Integer healthTimeout = null;

	/**
	 * Name of the host this instances runs on.
	 */
	private String hostName;

	/**
	 * See {@link #getMaxParallelProcess()}.
	 */
	@Nullable
	private Integer maxParallelProcess = null;

	/**
	 * Port of the instance on the host.
	 */
	private int port;

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * The corresponding external server.
	 * Mapped by {@link ExternalServer#getInstances()}
	 */
	private ExternalServer server;

	/**
	 * The host this instance is running on.
	 * Mapped by {@link ExternalHost#getInstances()}
	 */
	private ExternalHost host;

	/**
	 * The name of the instance defined in {@link ExternalServer#getInstances()}.
	 */
	private String name;

	//===============
	//--- Getters ---
	//===============

	/**
	 * Host name for sending callbacks from the instance to the platform.
	 * If the callback host of the instance is null, the value set for the external server is used.
	 *
	 * @return The callback host name.
	 */
	public String getCallbackHost() {
		return callbackHost == null ? server.getCallbackHost() : callbackHost;
	}

	/**
	 * Timeout in milliseconds for the health endpoint.
	 * If the request timeouts the instance is considered DOWN.
	 * If the timeout of the instance is null, the value set for the external server is used.
	 */
	public int getHealthTimeout() {
		return healthTimeout == null ? server.getHealthTimeout() : healthTimeout;
	}

	/**
	 * Returns the maximum number of processes that are allowed to run at the same time in this instance.
	 * If the value of the instance is null, the value set for the external server is used.
	 * Negative values allow an unlimited number of processes.
	 *
	 * @return The max number of processes.
	 */
	public int getMaxParallelProcess() {
		return maxParallelProcess == null ? server.getMaxParallelProcess() : maxParallelProcess;
	}

	/**
	 * Returns an ID uniquely identifying the instance across all instances of all external servers.
	 * The ID is in the form of [server_name][{@link #ID_SEPARATOR}][name].
	 *
	 * @return The ID.
	 */
	public String getId() {
		return server.getName() + ID_SEPARATOR + name;
	}

	/**
	 * URL of this instance.
	 *
	 * @return The URL.
	 */
	public String getUrl() {
		return host.getUrl() + ":" + port;
	}

}
