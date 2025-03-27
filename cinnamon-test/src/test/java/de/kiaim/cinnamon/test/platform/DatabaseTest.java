package de.kiaim.cinnamon.test.platform;

import de.kiaim.cinnamon.platform.exception.BadConfigurationNameException;
import de.kiaim.cinnamon.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.repository.DataSetRepository;
import de.kiaim.cinnamon.platform.repository.DataTransformationErrorRepository;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.StepService;
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
	public static final Long PROJECT_SEED = 123L;

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
	protected ProjectService projectService;
	@Autowired
	private StepService stepService;

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
		try {
			this.testProject = projectService.createProject(testUser, PROJECT_SEED);
		} catch (InternalApplicationConfigurationException e) {
			fail(e);
		}
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

		assertDoesNotThrow(() -> databaseService.storeConfiguration(CONFIGURATION_NAME, null, config, project),
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

	private void doCleanDatabase() {
		try {
			projectRepository.deleteAll();
			dataTransformationErrorRepository.deleteAll();
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
