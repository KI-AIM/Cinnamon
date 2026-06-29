package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter @Setter
public class ProjectInfo {
	@JsonProperty("id")
	private final String externalId;
	private final String name;
}
