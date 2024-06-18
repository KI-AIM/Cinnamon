package de.kiaim.platform.helper;

import de.kiaim.model.configuration.ColumnConfiguration;
import de.kiaim.model.configuration.DataConfiguration;
import org.springframework.stereotype.Service;

@Service
public class DataschemeGenerator {

	/**
	 * Creates a statement to create a table with the given name in the database based on the given DataConfiguration.
	 * @param dataConfiguration DataConfiguration for creating the schema.
	 * @param tableName Name of the table.
	 * @return Query in the form of a String.
	 */
	public String createSchema(final DataConfiguration dataConfiguration, final String tableName) {
		String query = "CREATE TABLE " + tableName +
		               "(" +
		               "%s" +
		               ");";
		StringBuilder columns = new StringBuilder();

		for (int i = 0; i < dataConfiguration.getConfigurations().size(); ++i) {
			final ColumnConfiguration columnConfiguration = dataConfiguration.getConfigurations().get(i);
			switch (columnConfiguration.getType()) {
				case BOOLEAN -> {
					columns.append(createColumnString(columnConfiguration.getName(), "boolean"));
				}
				case DATE -> {
					columns.append(createColumnString(columnConfiguration.getName(), "date"));
				}
				case DATE_TIME -> {
					columns.append(createColumnString(columnConfiguration.getName(), "timestamp"));
				}
				case DECIMAL -> {
					columns.append(createColumnString(columnConfiguration.getName(), "numeric"));
				}
				case INTEGER -> {
					columns.append(createColumnString(columnConfiguration.getName(), "integer"));
				}
				case STRING -> {
					columns.append(createColumnString(columnConfiguration.getName(), "character varying"));
				}
			}
			if (i < dataConfiguration.getConfigurations().size() - 1) {
				columns.append(",");
			}

		}
		return query.formatted(columns.toString());
	}

	private String createColumnString(final String columnName, final String dataType) {
		return "\"" + columnName + "\" " + dataType;
	}

}
