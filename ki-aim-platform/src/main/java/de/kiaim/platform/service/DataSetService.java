package de.kiaim.platform.service;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataSet;
import de.kiaim.platform.exception.BadColumnNameException;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.model.dto.DataSetSource;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.DataSetSourceSelector;
import de.kiaim.platform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * Provides functions for working with data sets.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class DataSetService {

	private final UserRepository userRepository;

	private final StepService stepService;

	@Autowired
	public DataSetService(final UserRepository userRepository, final StepService stepService) {
		this.userRepository = userRepository;
		this.stepService = stepService;
	}

	/**
	 * Encodes the given data set using the DataConfiguration of the user
	 * and the encoding configuration of the current request.
	 * Replaces all null values with the configured encoding.
	 *
	 * @param dataSet DataSet to encode.
	 * @return Encoded data set.
	 */
	public List<List<Object>> encodeDataRows(final DataSet dataSet) {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null) {
			return dataSet.getData();
		}

		UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		// User in security context has no data configuration
		user = userRepository.findById(user.getUsername()).get();

		final LoadDataRequest loadDataRequest = new LoadDataRequest();
		Set<DataTransformationErrorEntity> transformationErrors = new HashSet<>();
		Map<Integer, Integer> columnIndexMapping = new HashMap<>();

		final RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		if (ra instanceof ServletRequestAttributes) {
			final HttpServletRequest request = ((ServletRequestAttributes) ra).getRequest();
			final DataSetSourceSelector selector = DataSetSourceSelector.valueOf(
					request.getParameter("selector").trim().toUpperCase());
			final String jobName = request.getParameter("jobName");

			try {
				final DataSetEntity dataSetEntity = getDataSetEntityOrThrow(user.getProject(), new DataSetSource(selector, jobName));
				transformationErrors = dataSetEntity.getDataTransformationErrors();
				columnIndexMapping = getColumnIndexMapping(dataSetEntity.getDataConfiguration(), loadDataRequest.getColumnNames());
			} catch (BadStepNameException | BadDataSetIdException | BadColumnNameException ignored) {
			}

			final String defaultNullEncoding = request.getParameter("defaultNullEncoding");
			if (defaultNullEncoding != null) {
				loadDataRequest.setDefaultNullEncoding(defaultNullEncoding);
			}

			loadDataRequest.setMissingValueEncoding(request.getParameter("missingValueEncoding"));
			loadDataRequest.setFormatErrorEncoding(request.getParameter("formatErrorEncoding"));
			loadDataRequest.setValueNotInRangeEncoding(request.getParameter("valueNotInRangeEncoding"));
		}

		return encodeDataRows(dataSet, transformationErrors, columnIndexMapping, loadDataRequest);
	}

	/**
	 * Encodes the given data set using the given DataConfiguration and the given encoding configuration.
	 * Replaces all null values with the configured encoding.
	 *
	 * @param dataSet DataSet to encode.
	 * @param transformationErrors The transformation errors
	 * @param loadDataRequest Export settings.
	 * @return Encoded data set.
	 */
	public List<List<Object>> encodeDataRows(final DataSet dataSet,
	                                         final Set<DataTransformationErrorEntity> transformationErrors,
	                                         final Map<Integer, Integer> columnIndexMapping,
	                                         final LoadDataRequest loadDataRequest) {
		return encodeDataRows(dataSet, transformationErrors, 0, null, columnIndexMapping, loadDataRequest);
	}

	/**
	 * Encodes the given data set using the given DataConfiguration and the given encoding configuration.
	 * Replaces all null values with the configured encoding.
	 * Applies the given offset to the row indices of the transformation errors if indexMapping is null.
	 * If indexMapping is not null, uses the value at the given position as the row index.
	 *
	 * @param dataSet DataSet to encode.
	 * @param transformationErrors The transformation errors
	 * @param rowOffset Start row of the data set that is applied to the indices of the transformation errors.
	 * @param indexMapping Mapping for the index in the original data set.
	 * @param loadDataRequest Export settings.
	 * @return Encoded data set.
	 */
	public List<List<Object>> encodeDataRows(final DataSet dataSet,
	                                         final Set<DataTransformationErrorEntity> transformationErrors,
	                                         final int rowOffset,
	                                         @Nullable final List<Integer> indexMapping,
	                                         final Map<Integer, Integer> columnIndexMapping,
	                                         final LoadDataRequest loadDataRequest) {
		final List<List<Object>> data = dataSet.getData();

		String defaultNullEncoding = loadDataRequest.getDefaultNullEncoding();
		String missingValueEncoding = loadDataRequest.getMissingValueEncoding() == null
		                              ? defaultNullEncoding
		                              : loadDataRequest.getMissingValueEncoding();
		String formatErrorEncoding = loadDataRequest.getFormatErrorEncoding() == null
		                             ? defaultNullEncoding
		                             : loadDataRequest.getFormatErrorEncoding();
		String valueNotInRangeEncoding = loadDataRequest.getValueNotInRangeEncoding() == null
		                                 ? defaultNullEncoding
		                                 : loadDataRequest.getValueNotInRangeEncoding();

		if (!defaultNullEncoding.equals("$null") || !missingValueEncoding.equals("$null") ||
		    !formatErrorEncoding.equals("$null") || !valueNotInRangeEncoding.equals("$null")) {

			for (final DataTransformationErrorEntity transformationError : transformationErrors) {
				if (!columnIndexMapping.isEmpty() && !columnIndexMapping.containsKey(transformationError.getColumnIndex())) {
					// Column is not requested
					continue;
				}

				final var encodedValue = switch (transformationError.getErrorType()) {
					case CONFIG_ERROR, OTHER -> encodeValue(defaultNullEncoding, transformationError);
					case FORMAT_ERROR -> encodeValue(formatErrorEncoding, transformationError);
					case MISSING_VALUE -> encodeValue(missingValueEncoding, transformationError);
					case VALUE_NOT_IN_RANGE -> encodeValue(valueNotInRangeEncoding, transformationError);
				};

				final Integer columnIndex = columnIndexMapping.get(transformationError.getColumnIndex());
				final int rowIndex = indexMapping != null
				                  ? indexMapping.indexOf(transformationError.getRowIndex())
				                  : transformationError.getRowIndex() - rowOffset;
				data.get(rowIndex).set(columnIndex, encodedValue);
			}
		}

		return data;
	}

	/**
	 * Returns for the DataSetEntity for the given source from the given project.
	 *
	 * @param project       The project.
	 * @param dataSetSource The source.
	 * @return The DataSetEntity
	 * @throws BadDataSetIdException If no data set exist that complies with the selector.
	 */
	public DataSetEntity getDataSetEntityOrThrow(final ProjectEntity project,
	                                             final DataSetSource dataSetSource) throws BadDataSetIdException, BadStepNameException {
		DataSetEntity dataSet = null;

		switch (dataSetSource.getSelector())
		{
			case ORIGINAL -> {
				dataSet = project.getOriginalData().getDataSet();

				if (dataSet == null) {
					throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET,
					                                "The project '" + project.getId() +
					                                "' does not contain an original data set!");
				}

			}
			case JOB -> {
				final var process =  stepService.getProcess(dataSetSource.getJobName(), project);
				if (process instanceof DataProcessingEntity dataProcessing) {
					dataSet = dataProcessing.getDataSet();
				}

				if (dataSet == null) {
					throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET,
					                                "The project '" + project.getId() +
					                                "' does not contain a data set for job '" +
					                                dataSetSource.getJobName() + "'!");
				}

			}
		}

		return dataSet;
	}

	/**
	 * Maps the original index of the columns with the given names to their position in the list.
	 * If the list is empty, all columns will be added with their original index.
	 *
	 * @param dataConfiguration The DataConfiguration
	 * @param columnNames The column names to be mapped.
	 * @return Map from original index to index in list.
	 * @throws BadColumnNameException If no column with the given name exist.
	 */
	public Map<Integer, Integer> getColumnIndexMapping(final DataConfiguration dataConfiguration,
	                                                   final List<String> columnNames) throws BadColumnNameException {
		final Map<Integer, Integer> columnIndexMapping = new HashMap<>();

		if (columnNames.isEmpty()) {
			// If empty, all columns should be exported.
			for (int i = 0; i < dataConfiguration.getConfigurations().size(); i++) {
				final var columnConfiguration = dataConfiguration.getConfigurations().get(i);
				columnIndexMapping.put(columnConfiguration.getIndex(), i);
			}
		} else {

			for (int i = 0; i < columnNames.size(); i++) {
				final String columnName = columnNames.get(i);
				final var columnConfiguration = dataConfiguration.getColumnConfigurationByColumnName(columnName);

				if (columnConfiguration == null) {
					throw new BadColumnNameException(BadColumnNameException.NOT_FOUND, "Could not find column: " + columnName);
				}

				columnIndexMapping.put(columnConfiguration.getIndex(), i);
			}
		}

		return columnIndexMapping;
	}

	/**
	 * Encodes the value.
	 * "$null" replaces the value with 'null'.
	 * "$values" inserts the value from the given transformation error.
	 * All other values will not be modified.
	 *
	 * @param encoding The value or encoding to be used.
	 * @param transformationError The transformation error containing the original value.
	 * @return The encoded value.
	 */
	@Nullable
	private String encodeValue(final String encoding, final DataTransformationErrorEntity transformationError) {
		if (encoding.equals("$null")) {
			return null;
		}
		if (encoding.equals("$value")) {
			return transformationError.getOriginalValue();
		}
		return encoding;
	}
}
