package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Transactional(readOnly = true)
public interface DataTransformationErrorRepository extends CrudRepository<DataTransformationErrorEntity, Long> {

	long countByDataSetId(final Long dataSetId);

	Set<DataTransformationErrorEntity> findByDataSetIdAndRowIndexBetween(Long dataSet_id, int rowIndex, int rowIndex2);
}
