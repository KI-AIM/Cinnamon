package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.FloatRange;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;

@NoArgsConstructor
@Getter @Setter
public class HoldOutRequest {

	@Parameter(description = "Percentage of records that should be added to the hold-out split. Values can be between 0 and 1.", required = true,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = Float.class),
	                              examples = {
			                              @ExampleObject("0.3"),
	                              }))
	@NotNull
	@FloatRange(min = 0, max = 1)
	private Float holdOutPercentage;
}
