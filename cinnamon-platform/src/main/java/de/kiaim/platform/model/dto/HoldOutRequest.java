package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;

@NoArgsConstructor
@Getter @Setter
public class HoldOutRequest {

	@Parameter(description = "Percentage of records that should be added to the hold-out split.", required = true,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = Float.class),
	                              examples = {
			                              @ExampleObject("0.3"),
	                              }))
	@NotNull @Min(0) @Max(1)
	private Float holdOutPercentage;
}
