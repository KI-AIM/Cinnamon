package de.kiaim.cinnamon.test.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code int} field to be injected with the port of the GreenMail server started by
 * {@link GreenMailExtension}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GreenMailPort {
}
