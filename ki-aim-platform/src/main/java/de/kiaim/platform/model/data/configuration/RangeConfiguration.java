package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Schema(description = "Describes the range of value.",
        example = "{\"name\": \"RangeConfiguration\", \"minValue\": 0, \"maxValue\": 100}")
@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class RangeConfiguration implements Configuration {
	@Schema(description = "Minimum value.", example = "0", requiredMode = Schema.RequiredMode.REQUIRED,
	        anyOf = {DateData.class, DateTimeData.class, DecimalData.class, IntegerData.class})
	@NotNull(message = "The min value must not be empty!")
	private final Data minValue;

	@Schema(description = "Maximum value.", example = "100", requiredMode = Schema.RequiredMode.REQUIRED,
	        anyOf = {DateData.class, DateTimeData.class, DecimalData.class, IntegerData.class})
	@NotNull(message = "The max value must not be empty!")
	private final Data maxValue;
}
