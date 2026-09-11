package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validator to ensure that all column indices in a collection of {@link ColumnConfiguration} are unique.
 * Null values are considered valid, as they do not contribute to duplicates.
 * Can be used in conjunction with the {@link UniqueColumnIndexConstraint} annotation.
 *
 * @author Daniel Preciado-Marquez
 */
public class UniqueColumnIndexValidator implements ConstraintValidator<UniqueColumnIndexConstraint, List<ColumnConfiguration>> {

	@Override
	public boolean isValid(
			final @Nullable List<ColumnConfiguration> value,
			final ConstraintValidatorContext context) {
		if (value == null) {
			// If the collection is null, we consider it valid (no duplicates).
			return true;
		}

		var duplicateIndices = value.stream()
		                            .map(ColumnConfiguration::getIndex)
		                            .filter(Objects::nonNull) // Exclude null indices from the uniqueness check.
		                            .collect(Collectors.groupingBy(index -> index, Collectors.counting()))
		                            .entrySet()
		                            .stream()
		                            .filter(entry -> entry.getValue() > 1)
		                            .map(Map.Entry::getKey)
		                            .collect(Collectors.toSet());

		if (duplicateIndices.isEmpty()) {
			// No duplicates found, the collection is valid.
			return true;
		}

		// Report each duplicate index violation to the context for proper error reporting.
		for (int i = 0; i < value.size(); i++) {
			if (duplicateIndices.contains(value.get(i).getIndex())) {
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				       .addPropertyNode(null)
				       .inIterable()
				       .atIndex(i)
				       .addPropertyNode("index")
				       .addConstraintViolation()
				       .disableDefaultConstraintViolation();
			}
		}

		return false;
	}
}
