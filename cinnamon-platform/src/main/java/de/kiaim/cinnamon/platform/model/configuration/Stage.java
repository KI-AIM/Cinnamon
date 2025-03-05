package de.kiaim.cinnamon.platform.model.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class Stage {
	/**
	 * List of jobs contained in this stage.
	 */
	private List<String> jobs = new ArrayList<>();

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Name of the stage.
	 */
	private String stageName;

	/**
	 * Job objects based on {@link #jobs}.
	 */
	@JsonIgnore
	private List<Job> jobList = new ArrayList<>();
}
