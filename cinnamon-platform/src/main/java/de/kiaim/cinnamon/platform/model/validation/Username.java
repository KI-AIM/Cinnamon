package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.*;

/**
 * Collective annotation for validating a username. It combines the following constraints:
 * <ul>
 *     <li>{@link NotNull} - Ensures that the username is not null.</li>
 *     <li>{@link Size} - Ensures that the username is between 3 and 255 characters long.</li>
 *     <li>{@link UsernameAvailable} - Ensures that the username is available and not already taken by another user.</li>
 * </ul>
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {})
@NotNull(message = "Username is required!")
@Size(min = 1, max = 255, message = "Username must be between 1 and 255 characters long!")
@UsernameAvailable
public @interface Username {

	String message() default "Invalid username!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
