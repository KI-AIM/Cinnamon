package de.kiaim.platform.model.entity;

import de.kiaim.model.enumeration.TransformationErrorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class to represent a transformation error in the database.
 */
@Entity
@Getter
@NoArgsConstructor
public class DataTransformationErrorEntity {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * DataSet this transformation error corresponds to.
	 */
	@ManyToOne
	@JoinColumn(nullable = false)
	@Setter
	private DataSetEntity dataSet;

	/**
	 * Index of the row in the data set this transformation error corresponds to.
	 */
	@Setter
	private int rowIndex;

	/**
	 * Index of the column in the data set this transformation error corresponds to.
	 */
	@Setter
	private int columnIndex;

	/**
	 * The error type of this transformation error.
	 */
	@Setter
	private TransformationErrorType errorType;

	/**
	 * The original value in the corresponding data set.
	 */
	@Setter
	private String originalValue;
}
