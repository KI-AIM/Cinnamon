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
	 * <p>
	 * Performance note: PostgreSQL checks the foreign key constraint of this column for every inserted row.
	 * When storing millions of errors, this check takes around 90% of the insert time
	 * (3.5M errors: ~32 s with the constraint, ~3.5 s without).
	 * The constraint could be removed with
	 * {@code @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))},
	 * as the errors are deleted explicitly in {@code DatabaseService#deleteDataSet} anyway.
	 * However, the database would no longer prevent orphaned errors,
	 * and existing databases would need the constraint to be dropped manually, as {@code ddl-auto=update} does not
	 * drop constraints.
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
