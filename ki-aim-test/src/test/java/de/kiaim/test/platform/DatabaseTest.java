package de.kiaim.test.platform;

import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.DataTransformationErrorRepository;
import de.kiaim.platform.repository.ProjectRepository;
import de.kiaim.platform.repository.UserRepository;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

//@Transactional // Will block the DROP TABLE statement
public class DatabaseTest extends ContextRequiredTest {

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	protected DataTransformationErrorRepository dataTransformationErrorRepository;
	@Autowired
	ProjectRepository projectRepository;
	@Autowired
	UserRepository userRepository;

	@Autowired
	DatabaseService databaseService;
	@Autowired
	ProjectService projectService;

	protected UserEntity getTestUser() {
		Optional<UserEntity> userOptional = userRepository.findById("test_user");
		if (userOptional.isEmpty()) {
			fail("Set up failed. Could not find 'test_user'!");
		}
		return userOptional.get();
	}

	protected ProjectEntity getTestProject() {
		return projectService.getProject(getTestUser());
	}

	@BeforeEach
	@Transactional
	void setUpDatabase() {
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
		final UserEntity updatedUser = getTestUser();
		final ProjectEntity project = projectService.getProject(updatedUser);

		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, config, project),
		                   "The configuration could not be stored!");

		assertTrue(project.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(config, project.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	protected boolean existsDataConfigration(final long dataSetId) {
		return projectRepository.existsById(dataSetId);
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
			testUser.setProject(null);
			userRepository.save(testUser);

			projectRepository.deleteAll();
			databaseService.executeStatement("SELECT setval('project_entity_seq', 1, true)");
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
