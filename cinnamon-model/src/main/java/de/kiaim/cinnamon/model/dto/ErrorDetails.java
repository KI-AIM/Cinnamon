package de.kiaim.cinnamon.model.dto;

import lombok.*;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Set;

@Data @With
@NoArgsConstructor @AllArgsConstructor
public class ErrorDetails {
	@Nullable
	private String configurationName;

	@Nullable
	private Map<String, Set<String>> validationErrors;
}
