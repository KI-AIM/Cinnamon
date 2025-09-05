package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter @Setter
public class ExternalHost {

	/**
	 * Maximum number of processes that are allowed to run in parallel on the host.
	 * Negative values allow an unlimited number of processes.
	 * <p>
	 * The default is -1.
	 */
	private int maxParallelProcess = -1;

	/**
	 * URL of the host.
	 */
	private String url;

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * The name of the host defined in {@link CinnamonConfiguration#getExternalHost()}.
	 */
	private String name;

	/**
	 * Instances using this host.
	 * Mapped by {@link ExternalServerInstance#getHost()}
	 */
	private Set<ExternalServerInstance> instances = new HashSet<>();
}
