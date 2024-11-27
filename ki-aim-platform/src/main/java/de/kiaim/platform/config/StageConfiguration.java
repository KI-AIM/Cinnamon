package de.kiaim.platform.config;

import de.kiaim.platform.model.enumeration.Step;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class StageConfiguration {
	private List<Step> jobs = new ArrayList<>();
}
