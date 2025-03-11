package de.kiaim.cinnamon.test.model.validation;

import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DataConfigurationValidatorTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void beforeAll() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void afterAll() {
		validatorFactory.close();
	}

	@Test
	void valid() {
		var validConfig = DataConfigurationTestHelper.generateDataConfiguration();

		var violations = validator.validate(validConfig);
		assertTrue(violations.isEmpty(), "There should be no violations!");
	}

	@Test
	void spaceColumnName() {
		var config = DataConfigurationTestHelper.generateDataConfiguration();
		config.getConfigurations().get(0).setName("Invalid Name with spaces.");

		var violations = validator.validate(config);

		var iterator = violations.iterator();

		assertTrue(iterator.hasNext(), "Should have violations!");

		var violation = iterator.next();
		assertEquals("The column name must not contain space characters!", violation.getMessage(), "Violation has an unexpected message!");
		assertEquals("configurations[0].name", violation.getPropertyPath().toString(), "Violation has an unexpected path!");

		assertFalse(iterator.hasNext(), "Should have only one violation!");
	}

	@Test
	void undefinedDataType() {
		var config = DataConfigurationTestHelper.generateDataConfiguration();
		config.getConfigurations().get(0).setType(DataType.UNDEFINED);

		var violations = validator.validate(config);

		var iterator = violations.iterator();

		assertTrue(iterator.hasNext(), "Should have violations!");

		var violation = iterator.next();
		assertEquals("The data type must not be 'UNDEFINED'!", violation.getMessage(), "Violation has an unexpected message!");
		assertEquals("configurations[0].type", violation.getPropertyPath().toString(), "Violation has an unexpected path!");

		assertFalse(iterator.hasNext(), "Should have only one violation!");
	}
}
