package de.kiaim.cinnamon.test.platform;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.repository.DataSetRepository;
import de.kiaim.cinnamon.platform.repository.DataTransformationErrorRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.StepService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.util.TestDatabaseExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class DatabaseTest extends ContextRequiredTest {

	public static final String CONFIGURATION_NAME = "anonymization";
	public static final Long PROJECT_SEED = 123L;

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	protected DataTransformationErrorRepository dataTransformationErrorRepository;
	@Autowired
	DataSetRepository dataSetRepository;
	@Autowired
	UserRepository userRepository;

	@Autowired
	DatabaseService databaseService;
	@Autowired
	protected ProjectService projectService;
	@Autowired
	private StepService stepService;
	@Autowired
	private UserService userService;

	protected UserEntity testUser;
	protected ProjectEntity testProject;
	private UUID testProjectId;

	protected UserEntity getTestUser() {
		Optional<UserEntity> userOptional = userRepository.findByUsername("test_user");
		if (userOptional.isEmpty()) {
			fail("Set up failed. Could not find 'test_user'!");
		}
		return userOptional.get();
	}

	protected ProjectEntity getTestProject() {
		try {
			return projectService.getProject(getTestUser(), testProjectId);
		} catch (ApiException e) {
			fail(e);
		}
		return null;
	}

	@BeforeEach
	void setUpDatabase() {
		transactionTemplate.executeWithoutResult(status -> {
			if (connection == null) {
				connection = DataSourceUtils.getConnection(dataSource);

				if (activeDatabase == TestDatabaseExtension.TestDatabase.POSTGRES_CUSTOM) {
					// Clean database to prevent issues with canceled tests
					reportCleanupErrors(doCleanDatabase());
				}
			}

			this.testUser = getTestUser();
			try {
				this.testProject = userService.createProject(testUser, "Test Project", PROJECT_SEED);
				this.testProjectId = testProject.getExternalId();
			} catch (ApiException e) {
				fail(e);
			}
		});
	}

	@AfterEach
	protected void cleanDatabase() {
		try {
			reportCleanupErrors(doCleanDatabase());
		} finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
			connection = null;
		}
	}

	private void reportCleanupErrors(final List<String> errors) {
		if (!errors.isEmpty()) {
			fail(String.join(System.lineSeparator(), errors));
		}
	}

	protected void storeConfiguration(final String config) {
		final ProjectEntity project = getTestProject();

		assertDoesNotThrow(() -> databaseService.storeConfiguration(CONFIGURATION_NAME, config, project),
		                   "The configuration could not be stored!");
		testConfiguration(project, config);
	}

	protected void testConfiguration(final ProjectEntity project, final String config) {
		final ExternalConfiguration ec;
		try {
			ec = stepService.getExternalConfiguration(CONFIGURATION_NAME);
		} catch (final BadConfigurationNameException e) {
			fail(e);
			return;
		}
		final var bpc = project.getConfigurationList(ec).getConfigurations().get(0);

		assertNotNull(bpc, "The configuration has not been stored correctly in the process!");
		assertEquals(config, bpc.getConfiguration(), "The configuration has not been stored correctly!");
	}

	protected boolean existsDataSet(final long dataSetId) {
		return dataSetRepository.existsById(dataSetId);
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
		try {
			return databaseService.countEntries(dataSetId);
		} catch (InternalDataSetPersistenceException e) {
			fail(e);
			return 0;
		}
	}

	/**
	 * Deletes the data of every user and drops any dataset tables left behind.
	 * <p>
	 * Deleting a single user's data runs in its own transaction, and dropping the dataset tables afterward runs in
	 * another, separate transaction. Whenever no transaction is already active (e.g., for tests that opt out of the
	 * surrounding test transaction via {@code Propagation.NOT_SUPPORTED}), each of these becomes an independent,
	 * immediately-committing transaction. This matters because deleting one user's data can fail and mark its
	 * transaction rollback-only. Without this separation, that failure would silently roll back the dataset table
	 * cleanup too, even though it appeared to succeed, leaking tables into subsequent tests. Assertions about the
	 * outcome are therefore never raised in here, only collected and returned, so that a failure never rolls back
	 * cleanup work that already happened.
	 *
	 * @return A list of human-readable cleanup problems. Empty if cleanup was fully successful.
	 */
	private List<String> doCleanDatabase() {
		final List<String> errors = new ArrayList<>(deleteAllUserData());
		transactionTemplate.executeWithoutResult(status -> errors.addAll(cleanupDatabase()));
		return errors;
	}

	private List<String> deleteAllUserData() {
		final List<String> errors = new ArrayList<>();
		final List<Long> userIds = new ArrayList<>();
		userRepository.findAll().forEach(user -> userIds.add(user.getId()));

		for (final Long userId : userIds) {
			try {
				// Each user gets their own transaction so that a failure for one user does not roll back the deletions
				// already committed for the others.
				transactionTemplate.executeWithoutResult(status -> {
					final Optional<UserEntity> user = userRepository.findById(userId);
					if (user.isEmpty()) {
						return;
					}

					try {
						userService.deleteUserData(user.get());
					} catch (final InternalDataSetPersistenceException | InternalInvalidStateException e) {
						throw new IllegalStateException(e);
					}
				});
			} catch (final RuntimeException e) {
				errors.add("Failed to delete data of user with id '" + userId + "': " + e.getMessage());
			}
		}

		return errors;
	}

	private List<String> cleanupDatabase() {
		try {
			if (isH2()) {
				resetProjectSequenceH2();
			} else if (isPostgres()) {
				resetProjectSequencePostgres();
			} else {
				throw new IllegalStateException("Unsupported test database");
			}
		} catch (SQLException e) {
			return List.of("Failed to reset the project sequence: " + e.getMessage());
		}

		return dropDatasetTables();
	}

	private void resetProjectSequencePostgres() throws SQLException {
		databaseService.executeStatement("SELECT setval('project_entity_seq', 1, true)");
	}

	private void resetProjectSequenceH2() throws SQLException {
		databaseService.executeStatement("ALTER SEQUENCE project_entity_seq RESTART WITH 2");
	}

	private List<String> dropDatasetTables() {
		final List<TableName> tableNames = jdbcTemplate.query(
				"""
						SELECT table_schema, table_name
						FROM information_schema.tables
						WHERE lower(table_name) LIKE 'dataset_%'
						AND lower(table_schema) NOT LIKE 'pg_%'
						AND lower(table_schema) <> 'information_schema'
						""",
				(rs, rowNum) -> new TableName(
						rs.getString("table_schema"),
						rs.getString("table_name")
				)
		);

		for (final TableName tableName : tableNames) {
			jdbcTemplate.execute(
					"DROP TABLE IF EXISTS " +
					quoteIdentifier(tableName.schema()) +
					"." +
					quoteIdentifier(tableName.name())
			);
		}

		if (!tableNames.isEmpty()) {
			return List.of("Not all dataset tables have been dropped! In production, this would lead to orphaned data! Dropped tables: " +
			                tableNames);
		}

		return List.of();
	}

	private String quoteIdentifier(final String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	private record TableName(String schema, String name) {
	}

	private boolean isH2() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
		}
	}

	private boolean isPostgres() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
		}
	}

}
