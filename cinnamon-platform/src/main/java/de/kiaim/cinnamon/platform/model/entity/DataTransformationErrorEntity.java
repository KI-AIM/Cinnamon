package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Class to represent a transformation error in the database.
 */
@Entity
@Table(indexes = @Index(name = "idx_data_transformation_error_data_set_row",
                        columnList = "data_set_id, row_index"))
@Getter
@NoArgsConstructor
public class DataTransformationErrorEntity {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private @Nullable Long id;

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
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@Setter
	private TransformationErrorType errorType;

	/**
	 * The original value in the corresponding data set.
	 * Can be null if the original value was null.
	 */
	@Setter
	@Nullable
	private String originalValue;
}
