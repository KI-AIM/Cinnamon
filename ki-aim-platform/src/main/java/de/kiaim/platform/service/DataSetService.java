package de.kiaim.platform.service;

import de.kiaim.model.data.DataSet;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import de.kiaim.platform.model.entity.UserEntity;
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
				transformationErrors = user.getProject().getDataSets().get(step).getDataTransformationErrors();
			} catch (BadStepNameException ignored) {
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
	 * @return Encoded data set.
	 */
	public List<List<Object>> encodeDataRows(final DataSet dataSet,
	                                         final Set<DataTransformationErrorEntity> transformationErrors,
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
				data.get(transformationError.getRowIndex()).set(transformationError.getColumnIndex(), encodedValue);
			}
		}

		return data;
	}

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
