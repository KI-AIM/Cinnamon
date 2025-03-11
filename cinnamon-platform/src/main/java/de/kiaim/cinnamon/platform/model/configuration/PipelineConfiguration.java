package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class PipelineConfiguration {
	private List<String> stages = new ArrayList<>();

	//=========================
	//--- Automatically set ---
	//=========================

	private List<Stage> stageList = new ArrayList<>();
}
