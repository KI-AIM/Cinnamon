package de.kiaim.platform;

import de.kiaim.platform.repository.DataConfigurationRepository;
import de.kiaim.platform.service.DatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.fail;

@Transactional // Will block the DROP TABLE statement
public class DatabaseTest extends ContextRequiredTest {

	@Autowired
	DataSource dataSource;
	private Connection connection;

	@Autowired
	DatabaseService databaseService;

	@Autowired
	DataConfigurationRepository dataConfigurationRepository;

	@BeforeEach
	void setUp() {
		if (connection == null) {
			connection = DataSourceUtils.getConnection(dataSource);
		}
	}

	@AfterEach
	protected void cleanDatabase() {
		try {
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
			dataConfigurationRepository.deleteAll();
		} catch (SQLException ignored) {
		}
	}

	protected boolean existsDataConfigration(final long dataSetId) {
		return dataConfigurationRepository.existsById(dataSetId);
	}

	protected boolean existsTable(final long dataSetId) {
		final String existsQuery = "SELECT 1 FROM pg_class WHERE relname = ? AND relkind = 'r'";
		try (final PreparedStatement existTableQuery = connection.prepareStatement(existsQuery)) {
			existTableQuery.setString(1, databaseService.getTableName(dataSetId));
			try (final ResultSet resultSet = existTableQuery.executeQuery()) {
				return resultSet.next();
			}
		} catch (SQLException e) {
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
