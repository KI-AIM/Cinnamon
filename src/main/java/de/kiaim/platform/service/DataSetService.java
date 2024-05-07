package de.kiaim.platform.service;

import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.DataConfigurationEntity;
import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import de.kiaim.platform.model.entity.UserEntity;
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

import java.util.List;

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

		final RequestAttributes ra = RequestContextHolder.getRequestAttributes();
		if (ra instanceof ServletRequestAttributes) {
			final HttpServletRequest request = ((ServletRequestAttributes) ra).getRequest();

			final String defaultNullEncoding = request.getParameter("defaultNullEncoding");
			if (defaultNullEncoding != null) {
				loadDataRequest.setDefaultNullEncoding(defaultNullEncoding);
			}

			loadDataRequest.setMissingValueEncoding(request.getParameter("missingValueEncoding"));
			loadDataRequest.setFormatErrorEncoding(request.getParameter("formatErrorEncoding"));
			loadDataRequest.setValueNotInRangeEncoding(request.getParameter("valueNotInRangeEncoding"));
		}

		return encodeDataRows(dataSet, user.getDataConfiguration(), loadDataRequest);
	}

	/**
	 * Encodes the given data set using the given DataConfiguration and the given encoding configuration.
	 * Replaces all null values with the configured encoding.
	 *
	 * @param dataSet DataSet to encode.
	 * @param dataConfiguration The DataConfigurationEntity containing the transformation errors
	 * @return Encoded data set.
	 */
	public List<List<Object>> encodeDataRows(final DataSet dataSet, final DataConfigurationEntity dataConfiguration,
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

			for (final DataTransformationErrorEntity transformationError : dataConfiguration.getDataTransformationErrors()) {

				var blau = switch (transformationError.getErrorType()) {
					case CONFIG_ERROR, OTHER -> encodeValue(defaultNullEncoding, transformationError);
					case FORMAT_ERROR -> encodeValue(formatErrorEncoding, transformationError);
					case MISSING_VALUE -> encodeValue(missingValueEncoding, transformationError);
					case VALUE_NOT_IN_RANGE -> encodeValue(valueNotInRangeEncoding, transformationError);
				};
				data.get(transformationError.getRowIndex()).set(transformationError.getColumnIndex(), blau);
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
