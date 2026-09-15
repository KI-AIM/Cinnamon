package de.kiaim.cinnamon.platform.helper;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;

/**
 * Lazily formats the rows of a {@link DataSet} into PostgreSQL's COPY text format, one row at a
 * time. Formatting on demand (rather than building the whole payload as a single String upfront)
 * avoids doubling peak memory for large data sets, since the {@link DataSet} itself is already
 * fully resident in memory.
 *
 * @author Daniel Preciado-Marquez
 */
@Log4j2
public class DataSetCopyReader extends Reader {

	private final Iterator<DataRow> dataRows;
	private final List<ColumnConfiguration> columnConfigurations;

	private long rowNumber = 0;
	private @Nullable String currentLine = null;
	private int position;

	public DataSetCopyReader(final DataSet dataSet) {
		this.dataRows = dataSet.getDataRows().iterator();
		this.columnConfigurations = dataSet.getDataConfiguration().getConfigurations();
	}

	@Override
	public int read(final char[] cbuf, final int off, final int len) throws IOException {
		if (currentLine == null || position >= currentLine.length()) {
			if (!dataRows.hasNext()) {
				return -1;
			}
			currentLine = formatRow(dataRows.next());
			position = 0;
		}

		final int numberOfChars = Math.min(len, currentLine.length() - position);
		currentLine.getChars(position, position + numberOfChars, cbuf, off);
		position += numberOfChars;
		return numberOfChars;
	}

	private String formatRow(final DataRow dataRow) throws IOException {
		final StringBuilder line = new StringBuilder();
		final int numberOfColumns = columnConfigurations.size();

		// Add values from the dataset, account for rows containing too many values
		final int numberValuesCapped = Math.min(dataRow.getData().size(), numberOfColumns);
		int columnIndex = 0;
		for (; columnIndex < numberValuesCapped; columnIndex++) {
			if (columnIndex > 0) {
				line.append('\t');
			}
			appendValue(line, dataRow.getData().get(columnIndex));
		}

		// Fill missing values with null values to account for rows containing too few values
		for (; columnIndex < numberOfColumns; columnIndex++) {
			if (columnIndex > 0) {
				line.append('\t');
			}
			line.append("\\N");
		}

		// Add value for the is_hold_out flag and the row_index
		line.append('\t').append('f');
		line.append('\t').append(rowNumber++);
		line.append('\n');

		return line.toString();
	}

	private void appendValue(final StringBuilder line, final Data data) throws IOException {
		if (data.getValue() == null) {
			line.append("\\N");
			return;
		}

		switch (data.getDataType()) {
			case BOOLEAN -> line.append(((Boolean) data.getValue()) ? 't' : 'f');
			case DATE -> line.append(data.getValue());
			case DATE_TIME -> line.append(
					data.asDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")));
			case DECIMAL, INTEGER -> line.append(data.getValue());
			case TEXT, STRING -> appendEscaped(line, data.getValue().toString());
			case UNDEFINED -> {
				log.error("Undefined data type can not be persisted!");
				throw new IOException("Undefined data type can not be persisted!",
				                      new InternalDataSetPersistenceException(
						                      InternalDataSetPersistenceException.DATA_TYPE_STORE,
						                      "Undefined data type can not be persisted!"));
			}
		}
	}

	/**
	 * Appends the given value to the given line, escaping the characters that are significant in
	 * PostgreSQL's COPY text format (backslash, tab, newline, carriage return).
	 */
	private void appendEscaped(final StringBuilder line, final String value) {
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			switch (c) {
				case '\\' -> line.append("\\\\");
				case '\t' -> line.append("\\t");
				case '\n' -> line.append("\\n");
				case '\r' -> line.append("\\r");
				default -> line.append(c);
			}
		}
	}

	@Override
	public void close() {
		// Nothing to close; rows are formatted from the in-memory DataSet.
	}

}
