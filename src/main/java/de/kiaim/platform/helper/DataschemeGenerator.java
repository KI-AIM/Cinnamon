package de.kiaim.platform.helper;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.data.DataType;
import org.springframework.stereotype.Service;

@Service
public class DataschemeGenerator {

	public String createSchema(final DataConfiguration dataConfiguration, final long id) {

		String query = crateTableString(id);
		StringBuilder columns = new StringBuilder();

		for (final DataType dataType : dataConfiguration.getDataTypes()) {
			switch (dataType) {
				case BOOLEAN -> {
					columns.append(createColumnString("", "boolean"));
				}
				case DATE_TIME -> {
					columns.append(createColumnString("", "timestamp"));
				}
				case DECIMAL -> {
					columns.append(createColumnString("", "numeric"));
				}
				case INTEGER -> {
					columns.append(createColumnString("", "integer"));
				}
				case STRING -> {
					columns.append(createColumnString("", "character varying"));
				}
				case DATE -> {
					columns.append(createColumnString("", "date"));
				}
			}

		}

		return query.formatted(columns.toString());
	}

	private String createColumnString(final String columnName, final String dataType) {
		return columnName + " " + dataType + " NOT NULL,";
	}

	private String crateTableString(final long id) {
		return "CREATE TABLE data_set_" + id +
		       "(" +
		       "%s" +
		       ");";
	}

}
