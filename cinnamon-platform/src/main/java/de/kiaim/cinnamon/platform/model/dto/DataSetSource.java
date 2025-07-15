package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DataSetSourceSelector;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Identifies the {@link DataSetEntity} in requests concerning data sets.
 *
 * @author Daniel Preciado-Marquez
 */
@AllArgsConstructor
@Getter @Setter
public class DataSetSource {

	public static DataSetSource Original() {
		return new DataSetSource(DataSetSourceSelector.ORIGINAL, null);
	}

	public static DataSetSource Job(final String jobName) {
		return new DataSetSource(DataSetSourceSelector.JOB, jobName);
	}

	public static DataSetSource Protected() {
		return new DataSetSource(DataSetSourceSelector.PROTECTED, null);
	}

	/**
	 * Selects which type of source is used.
	 */
	@NotNull
	private DataSetSourceSelector selector;

	/**
	 * Specifies which job if {@link #selector} is {@link DataSetSourceSelector#JOB}.
	 */
	@Nullable
	private String jobName;
}
