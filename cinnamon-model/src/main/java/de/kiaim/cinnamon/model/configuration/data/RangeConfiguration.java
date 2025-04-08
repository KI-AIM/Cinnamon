package de.kiaim.cinnamon.model.configuration.data;

import de.kiaim.cinnamon.model.data.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Describes the range of value.",
        example = "{\"name\": \"RangeConfiguration\", \"minValue\": 0, \"maxValue\": 100}")
@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class RangeConfiguration implements Configuration {
	@Schema(description = "Minimum value.", example = "0", requiredMode = Schema.RequiredMode.REQUIRED,
	        oneOf = {LocalDate.class, LocalDateTime.class, Float.class, Integer.class})
	@NotNull(message = "The min value must not be empty!")
	private final Data minValue;

	@Schema(description = "Maximum value.", example = "100", requiredMode = Schema.RequiredMode.REQUIRED,
	        oneOf = {LocalDate.class, LocalDateTime.class, Float.class, Integer.class})
	@NotNull(message = "The max value must not be empty!")
	private final Data maxValue;
}
