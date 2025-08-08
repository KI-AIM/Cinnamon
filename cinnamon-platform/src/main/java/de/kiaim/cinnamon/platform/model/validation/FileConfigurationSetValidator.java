package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.lang.Nullable;

/**
 * Validates if the corresponding file configuration for the file type defined {@link FileConfiguration#getFileType()} is set.
 *
 * @author Daniel Preciado-Marquez
 */
public class FileConfigurationSetValidator implements ConstraintValidator<FileConfigurationSet, FileConfiguration> {

	@Override
	public boolean isValid(@Nullable final FileConfiguration value, final ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		if (value.getFileType() == FileType.CSV && value.getCsvFileConfiguration() == null) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("CSV file configuration must be set for CSV files!")
			       .addConstraintViolation();
			return false;
		}
		if (value.getFileType() == FileType.FHIR && value.getFhirFileConfiguration() == null) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("FHIR file configuration must be set for FHIR bundles!")
			       .addConstraintViolation();
			return false;
		}
		if (value.getFileType() == FileType.XLSX && value.getXlsxFileConfiguration() == null) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("XLSX file configuration must be set for XLSX files!")
			       .addConstraintViolation();
			return false;
		}

		return true;
	}
}
