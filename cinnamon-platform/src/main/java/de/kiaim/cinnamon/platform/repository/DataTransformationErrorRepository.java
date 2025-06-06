package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.DataTransformationErrorEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Transactional(readOnly = true)
public interface DataTransformationErrorRepository extends CrudRepository<DataTransformationErrorEntity, Long> {

	long countByDataSetId(final Long dataSetId);

	@Query(value = "SELECT COUNT(*) FROM (SELECT DISTINCT row_index FROM data_transformation_error_entity WHERE data_set_id = :dataSetId) AS temp", nativeQuery = true)
	long countDistinctRowIndexByDataSetId(Long dataSetId);

	Set<DataTransformationErrorEntity> findByDataSetIdAndRowIndexBetween(Long dataSet_id, int rowIndex, int rowIndex2);

	Set<DataTransformationErrorEntity> findByDataSetIdAndRowIndexIn(Long dataSet_id, List<Integer> rowIndex);
}
