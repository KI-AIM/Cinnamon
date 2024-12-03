package de.kiaim.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * Entity for steps of the type {@link de.kiaim.platform.model.enumeration.StepType#DATA_PROCESSING}.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter
public class DataProcessingEntity extends ExternalProcessEntity {

	/**
	 * The data set resulting from the processing.
	 */
	@OneToOne(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "data_set_id", referencedColumnName = "id")
	@Nullable
	private DataSetEntity dataSet = null;

	/**
	 * Links the given data set with this process.
	 * @param newDataSet The data set to link.
	 */
	public void setDataSet(@Nullable final DataSetEntity newDataSet) {
		final DataSetEntity oldDataSet = this.dataSet;
		this.dataSet = newDataSet;
		if (oldDataSet != null && oldDataSet.getJob() == this) {
			oldDataSet.setJob(null);
		}
		if (newDataSet != null && newDataSet.getJob() != this) {
			newDataSet.setJob(this);
		}
	}
}
