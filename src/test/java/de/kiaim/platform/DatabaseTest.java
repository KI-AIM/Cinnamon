package de.kiaim.platform;

import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.DataConfigurationRepository;
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

import static org.junit.jupiter.api.Assertions.fail;

//@Transactional // Will block the DROP TABLE statement
public class DatabaseTest extends ContextRequiredTest {

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	DatabaseService databaseService;

	@Autowired
	DataConfigurationRepository dataConfigurationRepository;

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
	void setUp() {
		if (connection == null) {
			connection = DataSourceUtils.getConnection(dataSource);
		}
	}

	@AfterEach
	@Transactional
	protected void cleanDatabase() {
		try {
			final UserEntity testUser = getTestUser();
			testUser.setDataConfiguration(null);
			userRepository.save(testUser);

			dataConfigurationRepository.deleteAll();
			databaseService.executeStatement("SELECT setval('data_configuration_entity_seq', 1, true)");
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

		DataSourceUtils.releaseConnection(connection, dataSource);
		connection = null;
	}

	protected boolean existsDataConfigration(final long dataSetId) {
		return dataConfigurationRepository.existsById(dataSetId);
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

}
