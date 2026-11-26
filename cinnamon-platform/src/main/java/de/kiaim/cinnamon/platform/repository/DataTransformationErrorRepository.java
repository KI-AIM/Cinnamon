package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.StoredTransformationError;
import de.kiaim.cinnamon.platform.model.entity.DataTransformationErrorEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Transactional(readOnly = true)
public interface DataTransformationErrorRepository extends CrudRepository<DataTransformationErrorEntity, Long> {

	long countByDataSetId(final Long dataSetId);

	@Query(value = "SELECT COUNT(*) FROM (SELECT DISTINCT row_index FROM data_transformation_error_entity WHERE data_set_id = :dataSetId) AS temp", nativeQuery = true)
	long countDistinctRowIndexByDataSetId(Long dataSetId);

	// The following queries return projections instead of entities,
	// so a large number of errors can be loaded without creating managed entities.

	List<StoredTransformationError> findByDataSetId(Long dataSetId);

	List<StoredTransformationError> findByDataSetIdAndColumnIndexIn(Long dataSetId, Collection<Integer> columnIndices);

	List<StoredTransformationError> findByDataSetIdAndRowIndexBetween(Long dataSetId, int startRowIndex,
	                                                                  int endRowIndex);

	List<StoredTransformationError> findByDataSetIdAndRowIndexIn(Long dataSetId, Collection<Integer> rowIndices);
}
