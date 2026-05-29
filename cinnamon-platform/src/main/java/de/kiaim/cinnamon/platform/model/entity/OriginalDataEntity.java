package de.kiaim.cinnamon.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
	private final FileEntity file = new FileEntity();

	/**
	 * Configuration for the dataset.
	 */
	@Embedded
	private final DatasetConfigurationEntity datasetConfiguration = new DatasetConfigurationEntity();

	/**
	 * The imported data set.
	 */
	@OneToOne(optional = true, fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "data_set_id", referencedColumnName = "id")
	@Nullable
	private DataSetEntity dataSet = null;

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

	/**
	 * Whether the dataset has a hold-out split.
	 * @return True if the dataset has a hold-out split.
	 */
	public boolean isHasHoldOut() {
		return this.dataSet != null && this.dataSet.isHasHoldOut();
	}
}
