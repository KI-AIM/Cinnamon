package de.kiaim.cinnamon.test.util;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension setting up a MockWebServer.
 *
 * @author Daniel Preciado-Marquez
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockWebServerExtension.class)
public @interface WithMockWebServer {
}
