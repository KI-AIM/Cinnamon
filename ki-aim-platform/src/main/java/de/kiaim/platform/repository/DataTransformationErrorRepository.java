package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Transactional(readOnly = true)
public interface DataTransformationErrorRepository extends CrudRepository<DataTransformationErrorEntity, Long> {

	long countByDataSetId(final Long dataSetId);

	@Query(value = "SELECT COUNT(*) FROM (SELECT DISTINCT row_index FROM data_transformation_error_entity WHERE data_set_id = :dataSetId) AS temp", nativeQuery = true)
	long countDistinctRowIndexByDataSetId(Long dataSetId);

	@Query(value = "SELECT DISTINCT d.row_index FROM data_transformation_error_entity d WHERE d.data_set_id = :dataSet_id ORDER BY d.row_index", nativeQuery = true)
	List<Integer> findRowIndexByDataSetIdOrderByRowIndexAsc(Long dataSet_id);

	@Query(value = "SELECT DISTINCT d.row_index FROM data_transformation_error_entity d WHERE d.data_set_id = :dataSet_id AND d.column_index IN :columnIndices ORDER BY d.row_index", nativeQuery = true)
	List<Integer> findRowIndexByDataSetIdAndColumnIndexInOrderByRowIndexAsc(Long dataSet_id, Collection<Integer> columnIndices);

	@Query(value = "SELECT DISTINCT d.row_index FROM data_transformation_error_entity d WHERE d.data_set_id = :dataSet_id ORDER BY d.row_index LIMIT :limit OFFSET :offset", nativeQuery = true)
	List<Integer> findRowIndexByDataSetIdOrderByRowIndexAsc(Long dataSet_id, int limit, int offset);

	@Query(value = "SELECT DISTINCT d.row_index FROM data_transformation_error_entity d WHERE d.data_set_id = :dataSet_id AND d.column_index IN :columnIndices ORDER BY d.row_index LIMIT :limit OFFSET :offset", nativeQuery = true)
	List<Integer> findRowIndexByDataSetIdAndColumnIndexInOrderByRowIndexAsc(Long dataSet_id, Collection<Integer> columnIndices, int limit, int offset);

	Set<DataTransformationErrorEntity> findByDataSetIdAndRowIndexBetween(Long dataSet_id, int rowIndex, int rowIndex2);

	Set<DataTransformationErrorEntity> findByDataSetIdAndRowIndexIn(Long dataSet_id, List<Integer> rowIndex);
}
