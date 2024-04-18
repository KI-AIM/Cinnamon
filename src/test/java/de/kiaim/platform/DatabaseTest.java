package de.kiaim.platform;

import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.PlatformConfigurationEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.PlatformConfigurationRepository;
import de.kiaim.platform.repository.DataTransformationErrorRepository;
import de.kiaim.platform.repository.UserRepository;
import de.kiaim.platform.service.DatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

//@Transactional // Will block the DROP TABLE statement
public class DatabaseTest extends ContextRequiredTest {

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	DatabaseService databaseService;

	@Autowired
	PlatformConfigurationRepository platformConfigurationRepository;

	@Autowired
	protected DataTransformationErrorRepository dataTransformationErrorRepository;

	@Autowired
	UserRepository userRepository;

	protected UserEntity getTestUser() {
		Optional<UserEntity> userOptional = userRepository.findById("test_user");
		if (userOptional.isEmpty()) {
			fail("Set up failed. Could not find 'test_user'!");
		}
		return userOptional.get();
	}

	@BeforeEach
	@Transactional
	void setUp() {
		if (connection == null) {
			connection = DataSourceUtils.getConnection(dataSource);
			// Clean database to prevent issues with canceled tests
			doCleanDatabase();
		}
	}

	@AfterEach
	@Transactional
	protected void cleanDatabase() {
		doCleanDatabase();
		DataSourceUtils.releaseConnection(connection, dataSource);
		connection = null;
	}

	protected void storeConfiguration(final String configName, final String config) {
		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, config, getTestUser()),
		                   "The configuration could not be stored!");

		final UserEntity updatedUser = getTestUser();

		final PlatformConfigurationEntity dataConfiguration = updatedUser.getPlatformConfiguration();
		assertNotNull(dataConfiguration, "The configuration has not been created!");
		assertTrue(dataConfiguration.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(config, dataConfiguration.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	protected boolean existsDataConfigration(final long dataSetId) {
		return platformConfigurationRepository.existsById(dataSetId);
	}

	protected boolean existsTable(final long dataSetId) {
		try {
			return databaseService.existsTable(dataSetId);
		} catch (InternalDataSetPersistenceException e) {
			fail(e);
			return false;
		}
	}

	protected int countEntries(final long dataSetId) {
		final String countQuery = "SELECT count(*) FROM " + databaseService.getTableName(dataSetId);
		try (final Statement countStatement = connection.createStatement()) {
			try (ResultSet resultSet = countStatement.executeQuery(countQuery)) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		} catch (SQLException e) {
			fail(e);
			return 0;
		}
	}

	private void doCleanDatabase() {
		try {
			final UserEntity testUser = getTestUser();
			testUser.setPlatformConfiguration(null);
			userRepository.save(testUser);

			platformConfigurationRepository.deleteAll();
			databaseService.executeStatement("SELECT setval('platform_configuration_entity_seq', 1, true)");
			databaseService.executeStatement(
					"""
							DO
							$do$
							DECLARE
							   _tbl text;
							BEGIN
							FOR _tbl  IN
							    SELECT quote_ident(table_schema) || '.' || quote_ident(table_name)
							    FROM   information_schema.tables
							    WHERE  table_name LIKE 'data_set_' || '%'
							    AND    table_schema NOT LIKE 'pg\\_%'
							LOOP
							    EXECUTE 'DROP TABLE ' || _tbl;
							END LOOP;
							END
							$do$;
							""");
		} catch (SQLException ignored) {
		}
	}

}
