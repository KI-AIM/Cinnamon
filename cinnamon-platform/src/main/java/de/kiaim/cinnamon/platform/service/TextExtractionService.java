package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.BooleanData;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.data.DecimalData;
import de.kiaim.cinnamon.model.data.IntegerData;
import de.kiaim.cinnamon.model.data.StringData;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.exception.BadArgumentException;
import de.kiaim.cinnamon.platform.exception.BadDataTypeException;
import de.kiaim.cinnamon.platform.exception.BadStateException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TextExtractionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TextExtractionService.class);
	private static final String RUNNING = "RUNNING";
	private static final String COMPLETED = "COMPLETED";
	private static final String FAILED = "FAILED";

	private final CinnamonConfiguration cinnamonConfiguration;
	private final DatabaseService databaseService;
	private final ProjectRepository projectRepository;
	// Text extraction is deliberately serialized because the configured local LLM is the throughput bottleneck.
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public TextExtractionService(final CinnamonConfiguration cinnamonConfiguration,
	                             final DatabaseService databaseService,
	                             final ProjectRepository projectRepository) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.databaseService = databaseService;
		this.projectRepository = projectRepository;
	}

	public synchronized JsonNode start(final ProjectEntity project, final JsonNode configuration) throws ApiException {
		final ProjectEntity current = projectRepository.findById(project.getId()).orElseThrow();
		if (RUNNING.equals(current.getTextExtractionStatus())) {
			throw new BadStateException(BadStateException.PROCESS_STARTED, "Text extraction is already running.");
		}
		if (current.getTextExtractionResult() != null &&
		    current.getTextExtractionResult().path("_applied").asBoolean(false)) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "The extracted data has already been added to the dataset.");
		}

		final ObjectNode request = createRequest(current, configuration);
		current.setTextExtractionConfiguration(configuration.deepCopy());
		current.setTextExtractionResult(null);
		current.setTextExtractionError(null);
		current.setTextExtractionStatus(RUNNING);
		projectRepository.save(current);

		executor.submit(() -> execute(current.getId(), request, configuration.deepCopy()));
		return state(current);
	}

	public JsonNode state(final ProjectEntity project) {
		final ObjectNode state = JsonNodeFactory.instance.objectNode();
		state.put("status", project.getTextExtractionStatus());
		state.set("configuration", project.getTextExtractionConfiguration());
		state.set("result", project.getTextExtractionResult());
		if (project.getTextExtractionError() == null) {
			state.putNull("error");
		} else {
			state.put("error", project.getTextExtractionError());
		}
		return state;
	}

	public JsonNode state(final ProjectEntity project, final boolean wide) {
		final ObjectNode state = (ObjectNode) state(project);
		if (wide && project.getTextExtractionResult() != null &&
		    project.getTextExtractionConfiguration() != null) {
			state.set("result", toWideFormat(project.getTextExtractionResult(),
			                                 project.getTextExtractionConfiguration().path("fields")).result());
		}
		return state;
	}

	public synchronized DataConfiguration apply(final ProjectEntity project, final boolean wide) throws ApiException {
		final ProjectEntity current = projectRepository.findById(project.getId()).orElseThrow();
		if (!COMPLETED.equals(current.getTextExtractionStatus()) || current.getTextExtractionResult() == null ||
		    current.getTextExtractionConfiguration() == null) {
			throw new BadStateException(BadStateException.CONFIGURATION,
			                            "Text extraction must be completed before its result can be added.");
		}
		JsonNode fields = current.getTextExtractionConfiguration().path("fields");
		JsonNode extractionResult = current.getTextExtractionResult();
		final List<ColumnConfiguration> longColumns = createColumns(fields);
		final WideExtraction wideExtraction = toWideFormat(extractionResult, fields);
		final List<ColumnConfiguration> wideColumns = createColumns(wideExtraction.fields());
		final List<ColumnConfiguration> requestedColumns = wide ? wideColumns : longColumns;

		if (extractionResult.path("_applied").asBoolean(false)) {
			final AppliedLayout appliedLayout = findAppliedLayout(current, extractionResult, longColumns, wideColumns);
			if (appliedLayout.wide() == wide || columnNames(appliedLayout.columns()).equals(columnNames(requestedColumns))) {
				return databaseService.exportOriginalDataConfiguration(current);
			}
			databaseService.restoreBeforeTextExtraction(current, columnNames(appliedLayout.columns()));
		}

		if (wide) {
			fields = wideExtraction.fields();
			extractionResult = wideExtraction.result();
		}
		final List<ColumnConfiguration> columns = requestedColumns;
		final Map<Integer, List<Data>> values = createValues(extractionResult.path("rows"), fields);
		final Map<Integer, Integer> sourceRows = createSourceRows(extractionResult.path("rows"));
		final DataConfiguration dataConfiguration = databaseService.appendExtractedColumns(
				current.getOriginalData().getDataSet(), columns, values, sourceRows);
		final ObjectNode appliedResult = (ObjectNode) current.getTextExtractionResult().deepCopy();
		appliedResult.put("_applied", true);
		appliedResult.put("_applied_wide", wide);
		current.setTextExtractionResult(appliedResult);
		projectRepository.save(current);
		return dataConfiguration;
	}

	private AppliedLayout findAppliedLayout(final ProjectEntity project, final JsonNode extractionResult,
	                                        final List<ColumnConfiguration> longColumns,
	                                        final List<ColumnConfiguration> wideColumns) throws ApiException {
		final List<String> configuredNames = databaseService.exportOriginalDataConfiguration(project).getColumnNames();
		if (extractionResult.has("_applied_wide")) {
			final boolean appliedWide = extractionResult.path("_applied_wide").asBoolean();
			return new AppliedLayout(appliedWide, appliedWide ? wideColumns : longColumns);
		}
		if (endsWith(configuredNames, columnNames(wideColumns))) {
			return new AppliedLayout(true, wideColumns);
		}
		if (endsWith(configuredNames, columnNames(longColumns))) {
			return new AppliedLayout(false, longColumns);
		}
		throw new BadArgumentException(BadArgumentException.INVALID_EXTRACTION_CONFIGURATION,
		                               "The previously applied extraction format could not be identified.");
	}

	private List<String> columnNames(final List<ColumnConfiguration> columns) {
		return columns.stream().map(ColumnConfiguration::getName).toList();
	}

	private boolean endsWith(final List<String> values, final List<String> suffix) {
		return values.size() >= suffix.size() &&
		       values.subList(values.size() - suffix.size(), values.size()).equals(suffix);
	}

	private List<ColumnConfiguration> createColumns(final JsonNode fields) throws BadArgumentException {
		final List<ColumnConfiguration> columns = new ArrayList<>();
		final List<JsonNode> flatFields = flattenFields(fields);
		final int primitiveCount = primitiveFieldCount(fields);
		final var orderedFields = JsonNodeFactory.instance.arrayNode();
		orderedFields.addObject().put("name", "text_extraction_id").put("type", "integer");
		for (int index = 0; index < flatFields.size(); index++) {
			if (index == primitiveCount && hasObjectFields(fields)) {
				orderedFields.addObject().put("name", "object_type").put("type", "string");
			}
			orderedFields.add(flatFields.get(index));
		}
		addColumns(orderedFields, columns);
		return columns;
	}

	private void addColumns(final JsonNode fields, final List<ColumnConfiguration> columns)
			throws BadArgumentException {
		for (final JsonNode field : flattenFields(fields)) {
			final String name = field.path("name").asText();
			if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
				throw new BadArgumentException(BadArgumentException.INVALID_EXTRACTION_CONFIGURATION,
				                               "Invalid extraction field name: '" + name + "'.");
			}
			final DataType type;
			try {
				type = DataType.valueOf(field.path("type").asText().toUpperCase());
			} catch (final IllegalArgumentException exception) {
				throw invalidField(name, exception);
			}
			if (type == DataType.TEXT || type == DataType.UNDEFINED) {
				throw invalidField(name, new IllegalArgumentException("Unsupported extraction type."));
			}

			final List<Configuration> configurations = new ArrayList<>();
			if (type == DataType.DATE) {
				configurations.add(new DateFormatConfiguration(field.path("format").asText()));
			} else if (type == DataType.DATE_TIME) {
				configurations.add(new DateTimeFormatConfiguration(field.path("format").asText()));
			}
			columns.add(new ColumnConfiguration(columns.size(), name, type, type.getDefaultScale(), configurations));
		}
	}

	private Map<Integer, List<Data>> createValues(final JsonNode rows, final JsonNode fields)
			throws BadArgumentException {
		final Map<Integer, List<Data>> values = new HashMap<>();
		final List<JsonNode> flatFields = flattenFields(fields);
		final boolean hasObjectFields = hasObjectFields(fields);
		final int primitiveCount = primitiveFieldCount(fields);
		try {
			for (final JsonNode row : rows) {
				final int rowIndex = row.path("row_index").intValue();
				final List<Data> rowValues = new ArrayList<>();
				rowValues.add(new IntegerData(row.path("text_extraction_id").intValue()));
				for (int index = 0; index < flatFields.size(); index++) {
					if (index == primitiveCount && hasObjectFields) {
						rowValues.add(new StringData(row.path("object_type").isNull()
						                                 ? null : row.path("object_type").asText(null)));
					}
					final JsonNode field = flatFields.get(index);
					rowValues.add(toData(row.get(field.path("name").asText()), field));
				}
				if (values.put(rowIndex, rowValues) != null) {
					throw new IllegalArgumentException("Duplicate row index.");
				}
			}
		} catch (final RuntimeException exception) {
			throw invalidField("result", exception);
		}
		return values;
	}

	private Map<Integer, Integer> createSourceRows(final JsonNode rows) throws BadArgumentException {
		final Map<Integer, Integer> sourceRows = new HashMap<>();
		try {
			for (final JsonNode row : rows) {
				final int rowIndex = row.path("row_index").intValue();
				final int sourceRowIndex = row.has("source_row_index")
				                           ? row.path("source_row_index").intValue()
				                           : rowIndex;
				if (sourceRows.put(rowIndex, sourceRowIndex) != null) {
					throw new IllegalArgumentException("Duplicate row index.");
				}
			}
		} catch (final RuntimeException exception) {
			throw invalidField("result", exception);
		}
		return sourceRows;
	}

	private List<JsonNode> flattenFields(final JsonNode fields) {
		final Map<String, JsonNode> result = new LinkedHashMap<>();
		for (final JsonNode field : fields) {
			if (!field.path("type").asText().equals("object")) {
				result.putIfAbsent(field.path("name").asText(), field);
			}
		}
		for (final JsonNode field : fields) {
			if (field.path("type").asText().equals("object")) {
				for (final JsonNode nestedField : field.path("fields")) {
					result.putIfAbsent(nestedField.path("name").asText(), nestedField);
				}
			}
		}
		return new ArrayList<>(result.values());
	}

	private int primitiveFieldCount(final JsonNode fields) {
		int count = 0;
		for (final JsonNode field : fields) {
			if (!field.path("type").asText().equals("object")) {
				count++;
			}
		}
		return count;
	}

	private boolean hasObjectFields(final JsonNode fields) {
		for (final JsonNode field : fields) {
			if (field.path("type").asText().equals("object")) {
				return true;
			}
		}
		return false;
	}

	WideExtraction toWideFormat(final JsonNode longResult, final JsonNode fields) {
		final Map<String, JsonNode> objectFields = new LinkedHashMap<>();
		for (final JsonNode field : fields) {
			if (field.path("type").asText().equals("object")) {
				objectFields.put(field.path("name").asText(), field);
			}
		}

		final Map<Integer, List<JsonNode>> rowsBySource = new LinkedHashMap<>();
		for (final JsonNode row : longResult.path("rows")) {
			rowsBySource.computeIfAbsent(sourceRowIndex(row), ignored -> new ArrayList<>()).add(row);
		}

		final Map<String, Integer> maximumOccurrences = new LinkedHashMap<>();
		objectFields.keySet().forEach(name -> maximumOccurrences.put(name, 1));
		for (final List<JsonNode> sourceRows : rowsBySource.values()) {
			final Map<String, Integer> occurrences = new HashMap<>();
			for (final JsonNode row : sourceRows) {
				final String objectType = row.path("object_type").asText();
				if (objectFields.containsKey(objectType)) {
					occurrences.merge(objectType, 1, Integer::sum);
				}
			}
			occurrences.forEach((name, count) -> maximumOccurrences.merge(name, count, Math::max));
		}

		final var wideFields = JsonNodeFactory.instance.arrayNode();
		for (final JsonNode field : fields) {
			if (!field.path("type").asText().equals("object")) {
				wideFields.add(field.deepCopy());
			}
		}
		for (final Map.Entry<String, JsonNode> object : objectFields.entrySet()) {
			for (int occurrence = 1; occurrence <= maximumOccurrences.get(object.getKey()); occurrence++) {
				for (final JsonNode nestedField : object.getValue().path("fields")) {
					final ObjectNode wideField = nestedField.deepCopy();
					wideField.put("name", wideFieldName(object.getKey(), occurrence,
					                                      nestedField.path("name").asText()));
					wideFields.add(wideField);
				}
			}
		}

		final Map<Integer, JsonNode> evidenceByRow = new HashMap<>();
		for (final JsonNode evidenceRow : longResult.path("evidence").path("rows")) {
			evidenceByRow.put(evidenceRow.path("row_index").intValue(), evidenceRow.path("fields"));
		}

		final ObjectNode wideResult = JsonNodeFactory.instance.objectNode();
		final var columns = wideResult.putArray("columns");
		columns.add("row_index");
		columns.add("text_extraction_id");
		for (final JsonNode field : wideFields) {
			columns.add(field.path("name").asText());
		}
		columns.add("source_text");
		final var wideRows = wideResult.putArray("rows");
		final var wideEvidenceRows = wideResult.putObject("evidence").putArray("rows");

		for (final Map.Entry<Integer, List<JsonNode>> source : rowsBySource.entrySet()) {
			final int wideRowIndex = wideRows.size();
			final List<JsonNode> sourceRows = source.getValue();
			final ObjectNode wideRow = wideRows.addObject();
			wideRow.put("row_index", wideRowIndex);
			wideRow.put("source_row_index", source.getKey());
			wideRow.put("text_extraction_id", firstInteger(sourceRows, "text_extraction_id",
			                                                     source.getKey() + 1));

			final ObjectNode wideEvidenceRow = wideEvidenceRows.addObject();
			wideEvidenceRow.put("row_index", wideRowIndex);
			wideEvidenceRow.put("source_row_index", source.getKey());
			final ObjectNode wideEvidence = wideEvidenceRow.putObject("fields");
			wideEvidence.set("text_extraction_id", emptyEvidence());

			for (final JsonNode field : fields) {
				if (!field.path("type").asText().equals("object")) {
					copyFirstValue(sourceRows, evidenceByRow, field.path("name").asText(),
					               wideRow, wideEvidence);
				}
			}

			final Map<String, Integer> occurrences = new HashMap<>();
			for (final JsonNode row : sourceRows) {
				final String objectType = row.path("object_type").asText();
				final JsonNode objectField = objectFields.get(objectType);
				if (objectField == null) {
					continue;
				}
				final int occurrence = occurrences.merge(objectType, 1, Integer::sum);
				final JsonNode rowEvidence = evidenceByRow.get(row.path("row_index").intValue());
				for (final JsonNode nestedField : objectField.path("fields")) {
					final String nestedName = nestedField.path("name").asText();
					final String wideName = wideFieldName(objectType, occurrence, nestedName);
					wideRow.set(wideName, valueOrNull(row.get(nestedName)));
					wideEvidence.set(wideName, evidenceOrEmpty(rowEvidence, nestedName));
				}
			}

			for (final JsonNode wideField : wideFields) {
				final String name = wideField.path("name").asText();
				if (!wideRow.has(name)) {
					wideRow.putNull(name);
					wideEvidence.set(name, emptyEvidence());
				}
			}
			copyFirstValue(sourceRows, evidenceByRow, "source_text", wideRow, null);
		}

		wideResult.set("errors", longResult.path("errors").deepCopy());
		return new WideExtraction(wideResult, wideFields);
	}

	private int sourceRowIndex(final JsonNode row) {
		if (row.has("source_row_index")) {
			return row.path("source_row_index").intValue();
		}
		if (row.hasNonNull("text_extraction_id")) {
			return row.path("text_extraction_id").intValue() - 1;
		}
		return row.path("row_index").intValue();
	}

	private int firstInteger(final List<JsonNode> rows, final String name, final int fallback) {
		for (final JsonNode row : rows) {
			if (row.hasNonNull(name)) {
				return row.path(name).intValue();
			}
		}
		return fallback;
	}

	private void copyFirstValue(final List<JsonNode> rows, final Map<Integer, JsonNode> evidenceByRow,
	                            final String name, final ObjectNode target, final ObjectNode targetEvidence) {
		JsonNode source = rows.get(0);
		for (final JsonNode row : rows) {
			if (row.hasNonNull(name)) {
				source = row;
				break;
			}
		}
		target.set(name, valueOrNull(source.get(name)));
		if (targetEvidence != null) {
			final JsonNode evidence = evidenceByRow.get(source.path("row_index").intValue());
			targetEvidence.set(name, evidenceOrEmpty(evidence, name));
		}
	}

	private JsonNode valueOrNull(final JsonNode value) {
		return value == null ? JsonNodeFactory.instance.nullNode() : value.deepCopy();
	}

	private JsonNode evidenceOrEmpty(final JsonNode evidenceFields, final String name) {
		return evidenceFields != null && evidenceFields.has(name)
		       ? evidenceFields.get(name).deepCopy()
		       : emptyEvidence();
	}

	private String wideFieldName(final String objectName, final int occurrence, final String nestedName) {
		return objectName + "_" + occurrence + "_" + nestedName;
	}

	private Data toData(final JsonNode value, final JsonNode field) {
		final DataType type = DataType.valueOf(field.path("type").asText().toUpperCase());
		if (value == null || value.isNull()) {
			return switch (type) {
				case BOOLEAN -> new BooleanData(null);
				case DATE -> new DateData(null);
				case DATE_TIME -> new DateTimeData(null);
				case DECIMAL -> new DecimalData(null);
				case INTEGER -> new IntegerData(null);
				case STRING -> new StringData(null);
				default -> throw new IllegalArgumentException("Unsupported extraction type.");
			};
		}
		return switch (type) {
			case BOOLEAN -> new BooleanData(value.booleanValue());
			case DATE -> new DateData(LocalDate.parse(value.asText(),
			                                               DateTimeFormatter.ofPattern(field.path("format").asText())));
			case DATE_TIME -> new DateTimeData(LocalDateTime.from(
					DateTimeFormatter.ofPattern(field.path("format").asText()).parse(value.asText())));
			case DECIMAL -> new DecimalData(value.floatValue());
			case INTEGER -> new IntegerData(value.intValue());
			case STRING -> new StringData(value.asText());
			default -> throw new IllegalArgumentException("Unsupported extraction type.");
		};
	}

	private BadArgumentException invalidField(final String name, final RuntimeException cause) {
		final var exception = new BadArgumentException(BadArgumentException.INVALID_EXTRACTION_CONFIGURATION,
		                                               "Invalid extracted value for field '" + name + "'.");
		exception.initCause(cause);
		return exception;
	}

	private ObjectNode createRequest(final ProjectEntity project, final JsonNode configuration) throws ApiException {
		final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
		if (dataSetEntity == null || !dataSetEntity.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No stored original dataset available.");
		}

		validateDateFormats(configuration.path("fields"));
		final DataSet sourceData = databaseService.exportDataSet(
				dataSetEntity,
				List.of(configuration.path("source_column").asText()),
				HoldOutSelector.ALL
		);
		if (sourceData.getDataConfiguration().getConfigurations().get(0).getType() != DataType.TEXT) {
			throw new BadDataTypeException(BadDataTypeException.TEXT_REQUIRED,
			                               "Text extraction requires a source column of type TEXT.");
		}

		final ObjectNode request = configuration.deepCopy();
		final var rows = request.putArray("rows");
		final int firstRow = hasUnrecognizedTextHeader(sourceData, configuration.path("source_column").asText())
		                     ? 1 : 0;
		for (int rowIndex = firstRow; rowIndex < sourceData.getDataRows().size(); rowIndex++) {
			final Object value = sourceData.getDataRows().get(rowIndex).getData().get(0).getValue();
			final ObjectNode row = rows.addObject()
			                           .put("row_index", rowIndex)
			                           .put("text_extraction_id", rowIndex - firstRow + 1);
			if (value == null) {
				row.putNull("text");
			} else {
				row.put("text", value.toString());
			}
		}
		return request;
	}

	private boolean hasUnrecognizedTextHeader(final DataSet sourceData, final String sourceColumn) {
		if (!sourceColumn.matches("column_\\d+") || sourceData.getDataRows().size() < 2) {
			return false;
		}
		final Object first = sourceData.getDataRows().get(0).getData().get(0).getValue();
		final Object second = sourceData.getDataRows().get(1).getData().get(0).getValue();
		return first != null && second != null && first.toString().matches("[A-Za-z_][A-Za-z0-9_]{0,63}") &&
		       second.toString().length() > 100;
	}

	private void execute(final Long projectId, final ObjectNode request, final JsonNode configuration) {
		try {
			final ObjectNode result = emptyResult(configuration.path("fields"));
			for (final JsonNode inputRow : request.path("rows")) {
				final int sourceRowIndex = inputRow.path("row_index").intValue();
				JsonNode partialResult;
				try {
					final ObjectNode singleRequest = request.deepCopy();
					singleRequest.putArray("rows").add(inputRow.deepCopy());
					partialResult = client().post()
					                      .uri("/extract-table")
					                      .bodyValue(singleRequest)
					                      .retrieve()
					                      .bodyToMono(JsonNode.class)
					                      .block();
					partialResult = formatDates(partialResult, configuration.path("fields"));
				} catch (final Exception exception) {
					LOGGER.error("Text extraction failed for project {} source row {}; inserting null values",
					             projectId, sourceRowIndex, exception);
					partialResult = failedResult(configuration.path("fields"), inputRow, exception);
				}
				appendResult(result, partialResult, sourceRowIndex,
				             inputRow.path("text_extraction_id").asInt(sourceRowIndex + 1));
				final ProjectEntity progress = projectRepository.findById(projectId).orElseThrow();
				progress.setTextExtractionResult(result.deepCopy());
				projectRepository.save(progress);
			}
			final ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
			project.setTextExtractionResult(result);
			project.setTextExtractionStatus(COMPLETED);
			project.setTextExtractionError(null);
			projectRepository.save(project);
		} catch (final Exception exception) {
			LOGGER.error("Text extraction failed for project {}", projectId, exception);
			projectRepository.findById(projectId).ifPresent(project -> {
				project.setTextExtractionStatus(FAILED);
				project.setTextExtractionError(exception.getMessage());
				projectRepository.save(project);
			});
		}
	}

	private ObjectNode emptyResult(final JsonNode fields) {
		final ObjectNode result = JsonNodeFactory.instance.objectNode();
		final var columns = result.putArray("columns");
		columns.add("row_index");
		columns.add("text_extraction_id");
		final List<JsonNode> flatFields = flattenFields(fields);
		for (int index = 0; index < flatFields.size(); index++) {
			if (index == primitiveFieldCount(fields) && hasObjectFields(fields)) {
				columns.add("object_type");
			}
			final JsonNode field = flatFields.get(index);
			columns.add(field.path("name").asText());
		}
		columns.add("source_text");
		result.putArray("rows");
		result.putObject("evidence").putArray("rows");
		result.putArray("errors");
		return result;
	}

	private JsonNode failedResult(final JsonNode fields, final JsonNode inputRow, final Exception exception) {
		final ObjectNode result = emptyResult(fields);
		final ObjectNode row = result.withArray("rows").addObject();
		final ObjectNode evidenceRow = result.with("evidence").withArray("rows").addObject();
		final ObjectNode evidenceFields = evidenceRow.putObject("fields");
		row.put("text_extraction_id",
		        inputRow.path("text_extraction_id").asInt(inputRow.path("row_index").intValue() + 1));
		evidenceFields.set("text_extraction_id", emptyEvidence());
		for (final JsonNode field : flattenFields(fields)) {
			final String name = field.path("name").asText();
			row.putNull(name);
			evidenceFields.set(name, emptyEvidence());
		}
		if (hasObjectFields(fields)) {
			row.putNull("object_type");
			evidenceFields.set("object_type", emptyEvidence());
		}
		if (inputRow.hasNonNull("text")) {
			row.set("source_text", inputRow.get("text"));
		} else {
			row.putNull("source_text");
		}
		result.withArray("errors").addObject()
		      .put("source_row_index", inputRow.path("row_index").intValue())
		      .put("message", exception.getMessage());
		return result;
	}

	private ObjectNode emptyEvidence() {
		return JsonNodeFactory.instance.objectNode()
		                          .putNull("value")
		                          .putNull("value_span")
		                          .putNull("evidence")
		                          .putNull("evidence_span");
	}

	private void appendResult(final ObjectNode result, final JsonNode partialResult,
	                          final int sourceRowIndex, final int textExtractionId) {
		final var rows = result.withArray("rows");
		final JsonNode partialEvidenceRows = partialResult.path("evidence").path("rows");
		for (int index = 0; index < partialResult.path("rows").size(); index++) {
			final int rowIndex = rows.size();
			final ObjectNode row = (ObjectNode) partialResult.path("rows").get(index).deepCopy();
			row.put("row_index", rowIndex);
			row.put("source_row_index", sourceRowIndex);
			row.put("text_extraction_id", textExtractionId);
			rows.add(row);

			final ObjectNode evidenceRow;
			if (index < partialEvidenceRows.size()) {
				evidenceRow = (ObjectNode) partialEvidenceRows.get(index).deepCopy();
			} else {
				evidenceRow = JsonNodeFactory.instance.objectNode();
				evidenceRow.putObject("fields");
			}
			evidenceRow.put("row_index", rowIndex);
			evidenceRow.put("source_row_index", sourceRowIndex);
			evidenceRow.with("fields").set("text_extraction_id", emptyEvidence());
			result.with("evidence").withArray("rows").add(evidenceRow);
		}
		for (final JsonNode error : partialResult.path("errors")) {
			result.withArray("errors").add(error.deepCopy());
		}
	}

	private void validateDateFormats(final JsonNode fields) throws BadArgumentException {
		for (final JsonNode field : fields) {
			final String type = field.path("type").asText();
			if (type.equals("object")) {
				validateDateFormats(field.path("fields"));
				continue;
			}
			if (type.equals("date") || type.equals("date_time")) {
				try {
					DateTimeFormatter.ofPattern(field.path("format").asText());
				} catch (final IllegalArgumentException exception) {
					throw invalidFormat(field, exception);
				}
			}
		}
	}

	private JsonNode formatDates(final JsonNode result, final JsonNode fields) throws BadArgumentException {
		for (final JsonNode field : fields) {
			final String type = field.path("type").asText();
			if (type.equals("object")) {
				formatDates(result, field.path("fields"));
				continue;
			}
			if (!type.equals("date") && !type.equals("date_time")) {
				continue;
			}

			final String name = field.path("name").asText();
			final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(field.path("format").asText());
			for (final JsonNode row : result.path("rows")) {
				formatDateValue((ObjectNode) row, name, type, formatter, field);
			}
			for (final JsonNode evidenceRow : result.path("evidence").path("rows")) {
				final JsonNode evidence = evidenceRow.path("fields").path(name);
				if (evidence instanceof ObjectNode evidenceObject) {
					formatDateValue(evidenceObject, "value", type, formatter, field);
				}
			}
		}
		return result;
	}

	private void formatDateValue(final ObjectNode container, final String property, final String type,
	                             final DateTimeFormatter formatter, final JsonNode field)
			throws BadArgumentException {
		final JsonNode value = container.get(property);
		if (value == null || value.isNull()) {
			return;
		}
		try {
			final String formatted = type.equals("date")
			                         ? LocalDate.parse(value.asText()).format(formatter)
			                         : formatter.format(DateTimeFormatter.ISO_DATE_TIME.parse(value.asText()));
			container.put(property, formatted);
		} catch (final DateTimeParseException exception) {
			throw invalidFormat(field, exception);
		}
	}

	private BadArgumentException invalidFormat(final JsonNode field, final RuntimeException cause) {
		final var exception = new BadArgumentException(
				BadArgumentException.INVALID_EXTRACTION_CONFIGURATION,
				"Invalid date format for extraction field '" + field.path("name").asText() + "'."
		);
		exception.initCause(cause);
		return exception;
	}

	private WebClient client() {
		final var host = cinnamonConfiguration.getExternalHost().get("text-extraction-host");
		if (host == null || host.getUrl() == null) {
			throw new IllegalStateException("Text extraction host is not configured.");
		}
		return WebClient.create(host.getUrl());
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedExtractions() {
		for (final ProjectEntity project : projectRepository.findAllByTextExtractionStatus(RUNNING)) {
			project.setTextExtractionStatus(FAILED);
			project.setTextExtractionError("The platform was restarted before the extraction completed.");
			projectRepository.save(project);
		}
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdownNow();
	}

	record WideExtraction(ObjectNode result, JsonNode fields) {
	}

	private record AppliedLayout(boolean wide, List<ColumnConfiguration> columns) {
	}
}
