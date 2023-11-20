package de.kiaim.platform.helper;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.data.DataType;
import org.springframework.stereotype.Service;

@Service
public class DataschemeGenerator {

	public String createSchema(final DataConfiguration dataConfiguration, final String tableName) {


		String query = "CREATE TABLE " + tableName +
		               "(" +
		               "%s" +
		               ")";
		StringBuilder columns = new StringBuilder();

		for (int i = 0; i < dataConfiguration.getDataTypes().size(); ++i) {
			final DataType dataType = dataConfiguration.getDataTypes().get(i);
			switch (dataType) {
				case BOOLEAN -> {
					columns.append(createColumnString("a", "boolean"));
				}
				case DATE -> {
					columns.append(createColumnString("f", "date"));
				}
				case DATE_TIME -> {
					columns.append(createColumnString("b", "timestamp"));
				}
				case DECIMAL -> {
					columns.append(createColumnString("c", "numeric"));
				}
				case INTEGER -> {
					columns.append(createColumnString("d", "integer"));
				}
				case STRING -> {
					columns.append(createColumnString("e", "character varying(255)"));
				}
			}
			if (i < dataConfiguration.getDataTypes().size() - 1) {
				columns.append(",");
			}

		}
		return query.formatted(columns.toString());
	}

	private String createColumnString(final String columnName, final String dataType) {
		return columnName + " " + dataType + " NOT NULL";
	}

	private String crateTableString(final long id) {
		return "CREATE TABLE data_set_" + id +
		       "(" +
		       "%s" +
		       ")";
	}

}
