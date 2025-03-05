package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.model.file.XlsxFileConfiguration;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * File configuration for XLSX files.
 * Contains metadata that is required in order to read a file.
 */
@Entity
@DiscriminatorValue(value = FileType.Values.XLSX)
@Getter @Setter
public class XlsxFileConfigurationEntity extends FileConfigurationEntity {

	/**
	 * If the first row of the file should be treated as the header row.
	 */
	@Nullable
	private Boolean hasHeader;

	public XlsxFileConfigurationEntity() {
		this.fileType = FileType.XLSX;
	}

	public XlsxFileConfigurationEntity(final XlsxFileConfiguration configuration) {
		this.hasHeader = configuration.isHasHeader();
		this.fileType = FileType.XLSX;
	}
}
