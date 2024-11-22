package de.kiaim.test.platform;

import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.DataSetRepository;
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
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class DatabaseTest extends ContextRequiredTest {

	public static final String CONFIGURATION_NAME = "anonymization";

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	protected DataTransformationErrorRepository dataTransformationErrorRepository;
	@Autowired
	DataSetRepository dataSetRepository;
	@Autowired
	ProjectRepository projectRepository;
	@Autowired
	UserRepository userRepository;

	@Autowired
	DatabaseService databaseService;
	@Autowired
	ProjectService projectService;

	protected UserEntity testUser;
	protected ProjectEntity testProject;

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

		this.testUser = getTestUser();
		this.testProject = projectService.createProject(testUser);
	}

	@AfterEach
	@Transactional
	protected void cleanDatabase() {
		doCleanDatabase();
		DataSourceUtils.releaseConnection(connection, dataSource);
		connection = null;
	}

	protected void storeConfiguration(final String config) {
		final UserEntity updatedUser = getTestUser();
		final ProjectEntity project = projectService.getProject(updatedUser);

		assertDoesNotThrow(() -> databaseService.storeConfiguration(CONFIGURATION_NAME, config, project),
		                   "The configuration could not be stored!");
		testConfiguration(project, config);
	}

	protected void testConfiguration(final ProjectEntity project, final String config) {
		final ExternalProcessEntity process = assertDoesNotThrow(
				() -> databaseService.getExternalProcessForConfigurationName(project, CONFIGURATION_NAME));
		assertNotNull(process.getConfiguration(), "The configuration has not been stored correctly in the process!");
		assertEquals(config, process.getConfiguration(), "The configuration has not been stored correctly!");
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

	private void doCleanDatabase() {
		try {
			if (this.testUser != null) {
				this.testUser.setProject(null);
				userRepository.save(this.testUser);
			}

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
							    WHERE  table_name LIKE 'dataset_' || '%'
							    AND    table_schema NOT LIKE 'pg\\_%'
							LOOP
							    EXECUTE 'DROP TABLE ' || _tbl;
							END LOOP;
							END
							$do$;
							""");
		} catch (SQLException e) {
			fail(e);
		}
	}

}
