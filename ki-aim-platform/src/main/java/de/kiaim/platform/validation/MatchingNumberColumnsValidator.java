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
		// Null values are validated by other constraints
		if (value.getFile() == null || value.getFileConfiguration() == null
		    || value.getConfiguration() == null || value.getConfiguration().getConfigurations() == null) {
			return true;
		}

		final int numberColumnsData = dataProcessorService.getDataProcessor(value.getFile())
		                                                  .getNumberColumns(value.getFile().getInputStream(),
		                                                                    value.getFileConfiguration());

		final var numberColumnsConfig = value.getConfiguration().getConfigurations().size();

		var isValid = true;
		if (numberColumnsData != numberColumnsConfig) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()
			                                             + " The data set has " + numberColumnsData
			                                             + " and the configuration " + numberColumnsConfig + " columns!")
			       .addPropertyNode("configuration")
			       .addPropertyNode("configurations")
			       .addConstraintViolation()
			       .disableDefaultConstraintViolation();
			isValid = false;
		}

		return isValid;
	}
}
