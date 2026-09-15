package de.kiaim.cinnamon.test.util;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class TestDatabaseExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {

	private static final String DATABASE_PROPERTY = "cinnamon.test.database";
	private static final String APPLICATION_PROPERTIES = "application.properties";
	private static final String APPLICATION_TEST_PROPERTIES = "application-test.properties";
	private static final StandardEnvironment ENVIRONMENT = createEnvironment();

	private static PostgreSQLContainer postgres;
	private static boolean initialized = false;
	private static TestDatabase activeDatabase;

	@Override
	public void beforeAll(final ExtensionContext context) {
		initialize();
	}

	@Override
	public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) throws IllegalAccessException {
		initialize();

		for (Class<?> type = testInstance.getClass(); type != null; type = type.getSuperclass()) {
			for (final Field field : type.getDeclaredFields()) {
				if (field.getType() == TestDatabase.class) {
					field.setAccessible(true);
					field.set(testInstance, activeDatabase);
				}
			}
		}
	}

	private static void initialize() {
		if (initialized) {
			return;
		}

		initialized = true;

		final TestDatabase database = resolveDatabase();

		switch (database) {
			case POSTGRES_TESTCONTAINERS -> usePostgres();
			case POSTGRES_CUSTOM -> useCustomDatabase();
			case AUTO -> useAutoDatabase();
		}
	}

	@Override
	public void afterAll(final ExtensionContext context) {
		/*
		 * Usually it is fine to let Testcontainers clean this up automatically.
		 * Keeping the container alive for the JVM lifetime also avoids restarting
		 * PostgreSQL for every test class.
		 */
	}

	private static TestDatabase resolveDatabase() {
		final String configuredValue = getConfiguredValue(DATABASE_PROPERTY);

		if (configuredValue == null || configuredValue.isBlank()) {
			return TestDatabase.AUTO;
		}

		return switch (configuredValue.trim().toLowerCase()) {
			case "auto" -> TestDatabase.AUTO;
			case "postgres_custom" -> TestDatabase.POSTGRES_CUSTOM;
			case "postgres_testcontainers" -> TestDatabase.POSTGRES_TESTCONTAINERS;
			default -> throw new IllegalArgumentException(
					"Unsupported test database value '" + configuredValue + "'. " +
					"Supported values are: auto, postgres_testcontainer, postgres_custom"
			);
		};
	}

	private static void useAutoDatabase() {
		final DataSourceProperties properties = getConfiguredDataSourceProperties();

		if (isCustomDatabaseConfigured(properties)) {
			if (isCustomDatabaseAvailable(properties)) {
				useCustomDatabase(properties);
				return;
			}

			System.out.println(
					"Configured test database is not available, falling back to Docker/Testcontainers: " +
					properties.getUrl()
			);
		}

		if (isDockerAvailable()) {
			usePostgres();
		} else {
			throw new IllegalStateException(
					"No test database is available. A custom PostgreSQL database must be configured, " +
					"or Docker must be available for Testcontainers. An H2 fallback is no longer " +
					"supported since DatabaseService.storeDataSet uses PostgreSQL's COPY protocol."
			);
		}
	}

	private static void usePostgres() {
		if (!isDockerAvailable()) {
			throw new IllegalStateException(
					"Test database is configured as PostgreSQL/Testcontainers, " +
					"but Docker is not available."
			);
		}

		if (postgres == null) {
			postgres = new PostgreSQLContainer("postgres:16-alpine");
			postgres.start();
		}

		System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
		System.setProperty("spring.datasource.username", postgres.getUsername());
		System.setProperty("spring.datasource.password", postgres.getPassword());
		System.setProperty("spring.datasource.driver-class-name", postgres.getDriverClassName());

		System.clearProperty("spring.jpa.database-platform");

		activeDatabase = TestDatabase.POSTGRES_TESTCONTAINERS;

		System.out.println("Using PostgreSQL Testcontainer for tests: " + postgres.getJdbcUrl());
	}

	private static void useCustomDatabase() {
		final DataSourceProperties properties = getConfiguredDataSourceProperties();

		if (!isCustomDatabaseConfigured(properties)) {
			throw new IllegalArgumentException(
					"Custom test database requires property 'spring.datasource.url'."
			);
		}

		if (!isCustomDatabaseAvailable(properties)) {
			throw new IllegalStateException(
					"Custom test database is configured but not available: " + properties.getUrl()
			);
		}

		useCustomDatabase(properties);
	}

	private static void useCustomDatabase(final DataSourceProperties properties) {
		System.setProperty("spring.datasource.url", properties.getUrl());

		if (properties.getUsername() != null) {
			System.setProperty("spring.datasource.username", properties.getUsername());
		}

		if (properties.getPassword() != null) {
			System.setProperty("spring.datasource.password", properties.getPassword());
		}

		if (properties.getDriverClassName() != null && !properties.getDriverClassName().isBlank()) {
			System.setProperty("spring.datasource.driver-class-name", properties.getDriverClassName());
		} else {
			System.clearProperty("spring.datasource.driver-class-name");
		}

		System.clearProperty("spring.jpa.database-platform");

		activeDatabase = TestDatabase.POSTGRES_CUSTOM;

		System.out.println("Using configured database for tests: " + properties.getUrl());
	}

	private static DataSourceProperties getConfiguredDataSourceProperties() {
		return Binder.get(ENVIRONMENT)
		             .bind("spring.datasource", Bindable.of(DataSourceProperties.class))
		             .orElseGet(DataSourceProperties::new);
	}

	private static StandardEnvironment createEnvironment() {
		final StandardEnvironment environment = new StandardEnvironment();

		final ClassPathResource applicationTestProperties = new ClassPathResource(APPLICATION_TEST_PROPERTIES);
		if (applicationTestProperties.exists()) {
			final Properties testProperties = new Properties();
			try {
				testProperties.load(applicationTestProperties.getInputStream());
			} catch (IOException e) {
				throw new IllegalStateException(
						"Failed to load " + APPLICATION_TEST_PROPERTIES + " from the classpath.", e);
			}

			environment.getPropertySources().addLast(
					new PropertiesPropertySource(APPLICATION_TEST_PROPERTIES, testProperties)
			);
		}

		final ClassPathResource applicationProperties = new ClassPathResource(APPLICATION_PROPERTIES);
		if (applicationProperties.exists()) {
			final Properties properties = new Properties();
			try {
				properties.load(applicationProperties.getInputStream());

			} catch (final IOException e) {
				throw new IllegalStateException("Failed to load " + APPLICATION_PROPERTIES + " from the classpath.", e);
			}

			environment.getPropertySources().addLast(
					new PropertiesPropertySource(APPLICATION_PROPERTIES, properties)
			);
		}

		return environment;
	}

	private static boolean isCustomDatabaseConfigured(final DataSourceProperties properties) {
		return properties.getUrl() != null && !properties.getUrl().isBlank();
	}

	private static boolean isCustomDatabaseAvailable(final DataSourceProperties properties) {
		try {
			if (properties.getDriverClassName() != null && !properties.getDriverClassName().isBlank()) {
				Class.forName(properties.getDriverClassName());
			}

			try (var ignored = DriverManager.getConnection(
					properties.getUrl(),
					properties.getUsername(),
					properties.getPassword()
			)) {
				return true;
			}
		} catch (ClassNotFoundException | SQLException ignored) {
			return false;
		}
	}

	private static boolean isDockerAvailable() {
		try {
			DockerClientFactory.instance().client();
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static String getConfiguredValue(final String propertyName) {
		final String systemProperty = System.getProperty(propertyName);

		if (systemProperty != null && !systemProperty.isBlank()) {
			return systemProperty;
		}

		final String environmentName = convertPropertyToEnvName(propertyName);
		final String environmentValue = System.getenv(environmentName);

		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue;
		}

		return ENVIRONMENT.getProperty(propertyName);
	}

	private static String convertPropertyToEnvName(final String property) {
		return property.replaceAll("\\.", "_").toUpperCase();
	}

	public enum TestDatabase {
		/**
		 * Automatically detects the database to use.
		 * The following order is used:
		 * <ol>
		 * <li>Custom database defined in application properties</li>
		 * <li>Docker/Testcontainers</li>
		 * </ol>
		 */
		AUTO,
		/**
		 * Uses a custom database defined in the application properties.
		 */
		POSTGRES_CUSTOM,
		/**
		 * Uses PostgreSQL/Testcontainers.
		 */
		POSTGRES_TESTCONTAINERS,
	}

}
