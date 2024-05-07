package de.kiaim.platform.validation;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.service.DatabaseService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;


public class DataConfigurationValidator implements ConstraintValidator<DataConfigurationConstraint, DataConfiguration> {

	private final DatabaseService databaseService;

	@Autowired
	public DataConfigurationValidator(final DatabaseService databaseService) {
		this.databaseService = databaseService;
	}

	@Override
	public boolean isValid(final DataConfiguration value, final ConstraintValidatorContext context) {
		return true;
	}
}
