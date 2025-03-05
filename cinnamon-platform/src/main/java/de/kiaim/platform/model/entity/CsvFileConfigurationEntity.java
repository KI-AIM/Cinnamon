package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.file.CsvFileConfiguration;
import de.kiaim.platform.model.file.FileType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * File configuration for CSV files.
 * Contains metadata that is required in order to read a file.
 */
@Entity
@DiscriminatorValue(value = FileType.Values.CSV)
@Getter @Setter
public class CsvFileConfigurationEntity extends FileConfigurationEntity {
	/**
	 * String used for separating columns.
	 */
	@Nullable
	private String columnSeparator;

	/**
	 * String used for separating columns.
	 */
	@Nullable
	private String lineSeparator;

	/**
	 * Quote char for escaping values.
	 */
	@Nullable
	private Character quoteChar;

	/**
	 * If the first row of the file should be treated as the header row.
	 */
	@Nullable
	private Boolean hasHeader;

	public CsvFileConfigurationEntity() {
		this.fileType = FileType.CSV;
	}

	public CsvFileConfigurationEntity(final CsvFileConfiguration configuration) {
		this.columnSeparator = configuration.getColumnSeparator();
		this.lineSeparator = configuration.getLineSeparator();
		this.quoteChar = configuration.getQuoteChar();
		this.hasHeader = configuration.getHasHeader();
		this.fileType = FileType.CSV;
	}
}
