package de.kiaim.cinnamon.model.dto;

import lombok.*;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

@Data @With
@NoArgsConstructor @AllArgsConstructor
public class ErrorDetails {
	@Nullable
	private String configurationName;

	@Nullable
	private Map<String, List<String>> validationErrors;
}
