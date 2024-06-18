package de.kiaim.platform.validation;

import de.kiaim.platform.ContextRequiredTest;
import de.kiaim.platform.TestModelHelper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DataConfigurationValidatorTest extends ContextRequiredTest {

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
		var validConfig = TestModelHelper.generateDataConfiguration();

		var violations = validator.validate(validConfig);
		assertTrue(violations.isEmpty(), "There should be no violations!");
	}

	@Test
	void spaceColumnName() {
		var config = TestModelHelper.generateDataConfiguration();
		config.getConfigurations().get(0).setName("Invalid Name with spaces.");

		var violations = validator.validate(config);

		var iterator = violations.iterator();

		assertTrue(iterator.hasNext(), "Should have violations!");

		var violation = iterator.next();
		assertEquals("The column name must not contain space characters!", violation.getMessage(), "Violation has an unexpected message!");
		assertEquals("configurations[0].name", violation.getPropertyPath().toString(), "Violation has an unexpected path!");

		assertFalse(iterator.hasNext(), "Should have only one violation!");
	}
}
