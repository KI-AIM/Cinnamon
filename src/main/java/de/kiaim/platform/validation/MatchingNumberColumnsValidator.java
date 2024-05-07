package de.kiaim.platform.validation;

import de.kiaim.platform.model.dto.ReadDataRequest;
import de.kiaim.platform.service.DataProcessorService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

public class MatchingNumberColumnsValidator implements ConstraintValidator<MatchingNumberColumnsConstraint, ReadDataRequest> {

	private final DataProcessorService dataProcessorService;

	@Autowired
	public MatchingNumberColumnsValidator(DataProcessorService dataProcessorService) {
		this.dataProcessorService = dataProcessorService;
	}

	@SneakyThrows
	@Override
	public boolean isValid(final ReadDataRequest value, final ConstraintValidatorContext context) {
		final var dataProcessor = dataProcessorService.getDataProcessor(value.getFile());

		final var numberColumnsData = dataProcessor.getNumberColumns(value.getFile().getInputStream(),
		                                                             value.getFileConfiguration());
		final var numberColumnsConfig = value.getConfiguration().getConfigurations().size();

		var isValid = true;

		if (numberColumnsData != numberColumnsConfig) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()
			                                             + " Expected " + numberColumnsData
			                                             + " but got " + numberColumnsConfig + "!")
			       .addPropertyNode("configuration")
			       .addConstraintViolation()
			       .disableDefaultConstraintViolation();
			return true;
		}

		return isValid;
	}
}
