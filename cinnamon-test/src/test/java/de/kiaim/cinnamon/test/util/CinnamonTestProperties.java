package de.kiaim.cinnamon.test.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
@ConfigurationProperties(prefix = "cinnamon.test")
@Getter @Setter
public class CinnamonTestProperties {
	/**
	 * Defines the database that should be used for the test.
	 */
	private TestDatabaseExtension.TestDatabase database = TestDatabaseExtension.TestDatabase.AUTO;
}
