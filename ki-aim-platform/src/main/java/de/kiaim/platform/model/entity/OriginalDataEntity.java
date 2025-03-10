package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Entity for containing the data and statistics of the original data set.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter
@NoArgsConstructor
public class OriginalDataEntity {

	/**
	 * ID and primary key.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;

	/**
	 * File containing the original data.
	 */
	@OneToOne(optional = true, fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "file_id", referencedColumnName = "id")
	@Setter
	@Nullable
	private FileEntity file = null;

	/**
	 * The imported data set.
	 */
	@OneToOne(optional = true, fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "data_set_id", referencedColumnName = "id")
	@Nullable
	private DataSetEntity dataSet = null;

	/**
	 * If the data set has a hold-out split.
	 */
	@Column(nullable = false)
	@Setter
	private boolean hasHoldOut = false;

	/**
	 * Seed that is used for generating the hold-out split.
	 */
	@Column(nullable = false)
	@Setter
	private double holdOutSeed = 0.0f;

	/**
	 * The percentage or rows that are assigned to the hold-out split.
	 */
	@Column(nullable = false)
	@Setter
	private float holdOutPercentage = 0.0f;

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@OneToOne(mappedBy = "originalData", optional = false, orphanRemoval = false, cascade = CascadeType.ALL)
	private ProjectEntity project;

	/**
	 * Creates a new entity for the given project.
	 * @param project The project.
	 */
	public OriginalDataEntity(final ProjectEntity project) {
		this.project = project;
	}

	/**
	 * Links the given data set with this entity.
	 * @param newDataSet The data set to link.
	 */
	public void setDataSet(@Nullable final DataSetEntity newDataSet) {
		final DataSetEntity oldDataSet = this.dataSet;
		this.dataSet = newDataSet;
		if (oldDataSet != null && oldDataSet.getOriginalData() == this) {
			oldDataSet.setOriginalData(null);
		}
		if (newDataSet != null && newDataSet.getOriginalData() != this) {
			newDataSet.setOriginalData(this);
		}
	}
}
