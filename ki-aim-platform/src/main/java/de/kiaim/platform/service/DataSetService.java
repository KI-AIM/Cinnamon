package de.kiaim.platform.service;

import de.kiaim.model.data.DataSet;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.Step;
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
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides functions for working with data sets.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class DataSetService {

	private final UserRepository userRepository;

	@Autowired
	public DataSetService(final UserRepository userRepository) {
		this.userRepository = userRepository;
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

		final RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		if (ra instanceof ServletRequestAttributes) {
			final HttpServletRequest request = ((ServletRequestAttributes) ra).getRequest();
			final Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
			final String stepName = pathVariables.get("stepName");

			try {
				final Step step = Step.getStepOrThrow(stepName);
				transformationErrors = getDataSetEntityOrThrow(user.getProject(), step).getDataTransformationErrors();
			} catch (BadStepNameException | BadDataSetIdException ignored) {
			}


			final String defaultNullEncoding = request.getParameter("defaultNullEncoding");
			if (defaultNullEncoding != null) {
				loadDataRequest.setDefaultNullEncoding(defaultNullEncoding);
			}

			loadDataRequest.setMissingValueEncoding(request.getParameter("missingValueEncoding"));
			loadDataRequest.setFormatErrorEncoding(request.getParameter("formatErrorEncoding"));
			loadDataRequest.setValueNotInRangeEncoding(request.getParameter("valueNotInRangeEncoding"));
		}

		return encodeDataRows(dataSet, transformationErrors, loadDataRequest);
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
	                                         final LoadDataRequest loadDataRequest) {
		return encodeDataRows(dataSet, transformationErrors, 0, null, loadDataRequest);
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

				final var encodedValue = switch (transformationError.getErrorType()) {
					case CONFIG_ERROR, OTHER -> encodeValue(defaultNullEncoding, transformationError);
					case FORMAT_ERROR -> encodeValue(formatErrorEncoding, transformationError);
					case MISSING_VALUE -> encodeValue(missingValueEncoding, transformationError);
					case VALUE_NOT_IN_RANGE -> encodeValue(valueNotInRangeEncoding, transformationError);
				};

				if (indexMapping != null) {
					data.get(indexMapping.indexOf(transformationError.getRowIndex())).set(transformationError.getColumnIndex(), encodedValue);
				} else {
					data.get(transformationError.getRowIndex() - rowOffset).set(transformationError.getColumnIndex(), encodedValue);
				}
			}
		}

		return data;
	}

	/**
	 * Returns for the DataSetEntity for the given Step from the given project.
	 * Allowed steps are {@link Step#VALIDATION} and steps of the type {@link de.kiaim.platform.model.enumeration.StepType#DATA_PROCESSING}.
	 *
	 * @param project The project.
	 * @param step The step.
	 * @return The DataSetEntity
	 * @throws BadDataSetIdException If no data set exist for the step.
	 */
	public DataSetEntity getDataSetEntityOrThrow(final ProjectEntity project,
	                                             final Step step) throws BadDataSetIdException {
		DataSetEntity dataSet = null;

		if (step == Step.VALIDATION) {
			dataSet = project.getOriginalData().getDataSet();
		} else {
			final ExternalProcessEntity process = project.getPipelines().get(0).getStageByStep(Step.EXECUTION)
			                                             .getProcesses().get(step);
			if (process instanceof DataProcessingEntity dataProcessing) {
				dataSet = dataProcessing.getDataSet();
			}
		}

		if (dataSet == null) {
			throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET, "The project '" + project.getId() +
			                                                                   "' does not contain a data set for step '" +
			                                                                   step.name() + "'!");
		}
		return dataSet;
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
