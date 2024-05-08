package de.kiaim.platform.validation;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UniqueColumnNamesValidator implements ConstraintValidator<UniqueColumnNamesConstraint, List<ColumnConfiguration>> {

	@Override
	public boolean isValid(@Nullable final List<ColumnConfiguration> value, final ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		final var duplicateNames = value.stream()
		                                .collect(Collectors.groupingBy(ColumnConfiguration::getName, Collectors.counting()))
		                                .entrySet()
		                                .stream()
		                                .filter(e -> e.getValue() > 1)
		                                .map(Map.Entry::getKey)
		                                .collect(Collectors.toSet());

		var uniqueColumnNames = true;

		for (int i = 0; i < value.size(); i++) {
			if (duplicateNames.contains(value.get(i).getName())) {
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				       .addPropertyNode(null)
				       .inIterable()
				       .atIndex(i)
				       .addPropertyNode("name")
				       .addConstraintViolation()
				       .disableDefaultConstraintViolation();
				uniqueColumnNames = false;
			}
		}

		return uniqueColumnNames;
	}
}
