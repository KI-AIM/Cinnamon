package de.kiaim.platform.service;

import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class DatabaseService {

	final DataSource dataSource;

	final DataschemeGenerator dataschemeGenerator;

	@Autowired
	public DatabaseService(DataSource dataSource, DataschemeGenerator dataschemeGenerator) {
		this.dataSource = dataSource;
		this.dataschemeGenerator = dataschemeGenerator;
	}

	public long store(final DataSet dataSet) {
		long id = 1;

		final String tableQuery = dataschemeGenerator.createSchema(dataSet.getDataConfiguration(), id);
		try (final Statement tableStatement = dataSource.getConnection().createStatement()) {
			tableStatement.execute(tableQuery);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return id;
	}

	public void delete(final DataSet dataSet) {

	}

}
