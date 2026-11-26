package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import de.kiaim.cinnamon.platform.model.FhirColumn;
import de.kiaim.cinnamon.platform.model.FhirTable;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.EnumFactory;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts FHIR R4 resources into a flat table and back, using the HAPI object model.
 * <p>
 * Columns are named by the position-based path of a primitive value, e.g. {@code Patient.name[0].given[1]}:
 * <ul>
 *     <li>Indices are only added for repeating elements and are the actual position in the list.</li>
 *     <li>Choice elements are named after the actual type, e.g. {@code Observation.valueQuantity.value}.</li>
 *     <li>The id of a resource contains only the id part, without the resource type, base URL or version.</li>
 *     <li>Extensions of primitive values ({@code _birthDate} in JSON) are placed below the primitive,
 *     e.g. {@code Patient.birthDate.extension[0].url}. They are also kept if the primitive has no value.</li>
 * </ul>
 * Each column carries the FHIR datatype of its values, which is used to derive the Cinnamon data type.
 * <p>
 * Narratives ({@code text.div}) are flattened as plain text using {@link FhirNarrativeText} with the type
 * {@link #NARRATIVE_TYPE}. Narratives without text are removed when unflattening, as FHIR requires a div.
 * <p>
 * Not supported yet: contained resources.
 * <p>
 * The class is thread-safe; HAPI model objects are created per call and the {@link FhirContext} is thread-safe.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Slf4j
public class FhirFlattener {

	/**
	 * FHIR type of the narrative text column.
	 */
	public static final String NARRATIVE_TYPE = "xhtml";

	/**
	 * Name of the narrative element, which is not part of the HAPI children.
	 */
	private static final String NARRATIVE_DIV = "div";

	/**
	 * Properties that are not flattened.
	 */
	private static final Set<String> SKIPPED_PROPERTIES = Set.of("contained");

	/**
	 * Pattern of a path segment with an optional index, e.g. {@code given[1]}.
	 */
	private static final Pattern SEGMENT_PATTERN = Pattern.compile("^([A-Za-z0-9_]+)(?:\\[(\\d+)])?$");

	private final FhirContext fhirContext;

	/**
	 * Cache of resolved properties, keyed by the FHIR type of the parent and the segment name.
	 */
	private final Map<String, ResolvedProperty> propertyCache = new ConcurrentHashMap<>();

	public FhirFlattener(final FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

	/**
	 * Flattens the resources of the given type into a table.
	 * Resources of other types are skipped.
	 * The columns are the union of the paths present in the resources, ordered like the elements in the FHIR
	 * specification and by index for repeating elements.
	 *
	 * @param resources    The resources.
	 * @param resourceType The resource type to be included in the table.
	 * @return The table.
	 */
	public FhirTable flatten(final Iterable<? extends IBaseResource> resources, final String resourceType) {
		final Map<String, ColumnInfo> columns = new HashMap<>();
		final List<Map<String, String>> resourceValues = new ArrayList<>();

		for (final IBaseResource resource : resources) {
			if (!(resource instanceof Resource r4Resource) || !resourceType.equals(r4Resource.fhirType())) {
				continue;
			}

			final Map<String, String> values = new HashMap<>();
			walk(r4Resource, resourceType, List.of(), values, columns);
			resourceValues.add(values);
		}

		final List<Map.Entry<String, ColumnInfo>> sortedColumns =
				columns.entrySet().stream().sorted(Map.Entry.comparingByValue(ColumnInfo.ORDER)).toList();
		final List<String> columnNames = sortedColumns.stream().map(Map.Entry::getKey).toList();
		final List<FhirColumn> fhirColumns = new ArrayList<>(sortedColumns.size());
		for (final Map.Entry<String, ColumnInfo> column : sortedColumns) {
			fhirColumns.add(new FhirColumn(column.getKey(), column.getValue().fhirType(),
			                               column.getValue().allowedCodes()));
		}

		final List<List<@Nullable String>> rows = new ArrayList<>(resourceValues.size());
		for (final Map<String, String> values : resourceValues) {
			final List<@Nullable String> row = new ArrayList<>(columnNames.size());
			for (final String columnName : columnNames) {
				row.add(values.get(columnName));
			}
			rows.add(row);
		}

		return new FhirTable(fhirColumns, rows);
	}

	/**
	 * Creates a resource from a flattened row.
	 * Values that cannot be set, e.g. because they do not match the FHIR datatype of the path,
	 * are skipped and logged.
	 *
	 * @param resourceType The type of the resource to create.
	 * @param columnNames  The column names containing the paths as created by {@link #flatten}.
	 * @param values       The values of the row, aligned with the column names. Null or empty values are skipped.
	 * @return The resource.
	 */
	public Resource unflatten(final String resourceType, final List<String> columnNames,
	                          final List<@Nullable String> values) {
		final Resource resource = (Resource) fhirContext.getResourceDefinition(resourceType).newInstance();

		for (int i = 0; i < columnNames.size(); i++) {
			final String value = values.get(i);
			if (value == null || value.isEmpty()) {
				continue;
			}

			try {
				setValue(resource, columnNames.get(i), value);
			} catch (final RuntimeException e) {
				log.warn("Skipping value for '{}' that cannot be converted to FHIR: {}", columnNames.get(i),
				         e.getMessage());
			}
		}

		removeNarrativeWithoutText(resource);

		return resource;
	}

	/**
	 * Returns the resource type of the given flattened columns, i.e. the first segment of the paths.
	 *
	 * @param columnNames The column names.
	 * @return The resource type.
	 * @throws IllegalArgumentException If there are no columns or the columns belong to different resource types.
	 */
	public static String getResourceType(final List<String> columnNames) {
		if (columnNames.isEmpty()) {
			throw new IllegalArgumentException("Cannot determine the resource type without columns.");
		}

		final String resourceType = columnNames.get(0).split("\\.", 2)[0];
		for (final String columnName : columnNames) {
			if (!columnName.startsWith(resourceType + ".")) {
				throw new IllegalArgumentException(
						"Column '" + columnName + "' does not belong to the resource type '" + resourceType + "'.");
			}
		}
		return resourceType;
	}

	/**
	 * Recursively collects all primitive values of the given element.
	 *
	 * @param base    The element.
	 * @param path    The path of the element.
	 * @param key     The sort key of the element.
	 * @param values  Collected values by path.
	 * @param columns Collected column information by path.
	 */
	private void walk(final Base base, final String path, final List<SegmentKey> key,
	                  final Map<String, String> values, final Map<String, ColumnInfo> columns) {
		final List<Property> properties = base.children();
		for (int position = 0; position < properties.size(); position++) {
			final Property property = properties.get(position);
			if (!property.hasValues() || SKIPPED_PROPERTIES.contains(property.getName())) {
				continue;
			}

			final boolean repeating = property.getMaxCardinality() > 1;
			final List<Base> propertyValues = property.getValues();
			for (int index = 0; index < propertyValues.size(); index++) {
				final Base value = propertyValues.get(index);
				if (value == null || value.isEmpty()) {
					continue;
				}

				final String name = getElementName(property, value);
				final String childPath = path + "." + name + (repeating ? "[" + index + "]" : "");
				final List<SegmentKey> childKey = new ArrayList<>(key.size() + 1);
				childKey.addAll(key);
				childKey.add(new SegmentKey(position, name, repeating ? index : -1));

				if (value.isPrimitive()) {
					final String primitiveValue = base instanceof Resource resource && "id".equals(property.getName())
					                              ? resource.getIdElement().getIdPart()
					                              : value.primitiveValue();
					if (primitiveValue != null) {
						columns.putIfAbsent(childPath,
						                    new ColumnInfo(childKey, value.fhirType(), getAllowedCodes(value)));
						values.put(childPath, primitiveValue);
					}

					// Primitives can carry extensions (_birthDate in JSON), even without a value
					if (value instanceof Element element && element.hasExtension()) {
						walk(value, childPath, childKey, values, columns);
					}
				} else {
					walk(value, childPath, childKey, values, columns);
				}
			}
		}

		// The div of a narrative is not part of the children, add it after the status like in the specification
		if (base instanceof Narrative narrative && narrative.hasDiv()) {
			final String text = FhirNarrativeText.toPlainText(narrative.getDiv());
			if (!text.isEmpty()) {
				final String childPath = path + "." + NARRATIVE_DIV;
				final List<SegmentKey> childKey = new ArrayList<>(key.size() + 1);
				childKey.addAll(key);
				childKey.add(new SegmentKey(properties.size(), NARRATIVE_DIV, -1));
				columns.putIfAbsent(childPath, new ColumnInfo(childKey, NARRATIVE_TYPE, null));
				values.put(childPath, text);
			}
		}
	}

	/**
	 * Returns the codes allowed for the given value.
	 * HAPI represents codes with a required binding as {@link Enumeration} of a Java enum containing all codes
	 * of the value set. Other codes are represented as {@code CodeType} and are not restricted.
	 *
	 * @param value The primitive value.
	 * @return The allowed codes or null if the value is not restricted.
	 */
	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static List<String> getAllowedCodes(final Base value) {
		if (!(value instanceof Enumeration<?> enumeration) || enumeration.getValue() == null) {
			return null;
		}
		final Enum<?> constant = enumeration.getValue();

		final EnumFactory factory = enumeration.getEnumFactory();
		final List<String> codes = new ArrayList<>();
		for (final Enum<?> candidate : constant.getDeclaringClass().getEnumConstants()) {
			// HAPI enums contain a NULL constant for unknown codes
			if (candidate.name().equals("NULL")) {
				continue;
			}
			final String code = factory.toCode(candidate);
			if (code != null) {
				codes.add(code);
			}
		}
		return codes.isEmpty() ? null : List.copyOf(codes);
	}

	/**
	 * Returns the element name of the value, resolving choice elements to the actual type,
	 * e.g. {@code value[x]} containing a Quantity becomes {@code valueQuantity}.
	 */
	private static String getElementName(final Property property, final Base value) {
		final String name = property.getName();
		if (!name.endsWith("[x]")) {
			return name;
		}

		final String type = value.fhirType();
		return name.substring(0, name.length() - 3) + Character.toUpperCase(type.charAt(0)) + type.substring(1);
	}

	/**
	 * Sets the value at the given path, creating all intermediate elements.
	 *
	 * @param resource The resource.
	 * @param path     The path as created by {@link #flatten}.
	 * @param value    The value to set.
	 */
	private void setValue(final Resource resource, final String path, final String value) {
		final String[] segments = path.split("\\.");
		if (!segments[0].equals(resource.fhirType())) {
			throw new IllegalArgumentException("Path does not start with the resource type " + resource.fhirType());
		}

		Base current = resource;
		for (int i = 1; i < segments.length - 1; i++) {
			current = getOrCreateChild(current, segments[i]);
		}

		final String lastSegment = segments[segments.length - 1];
		if (current instanceof Narrative narrative && lastSegment.equals(NARRATIVE_DIV)) {
			narrative.setDiv(FhirNarrativeText.toXhtml(value));
			return;
		}
		current = getOrCreateChild(current, lastSegment);

		if (!(current instanceof PrimitiveType<?> primitive)) {
			throw new IllegalArgumentException("Path does not point to a primitive value");
		}

		try {
			primitive.setValueAsString(value);
		} catch (final RuntimeException e) {
			// HAPI keeps the raw string of invalid values (e.g. unknown codes) and would encode it
			clear(primitive);
			throw e;
		}
	}

	/**
	 * Removes the narrative if it has no text.
	 * FHIR requires a div for narratives, but the text column can be empty, e.g. after the anonymization,
	 * while the status is still set.
	 *
	 * @param resource The resource.
	 */
	private static void removeNarrativeWithoutText(final Resource resource) {
		if (resource instanceof DomainResource domainResource && domainResource.hasText() &&
		    !domainResource.getText().hasDiv()) {
			domainResource.setText(null);
		}
	}

	@SuppressWarnings("unchecked")
	private static void clear(final PrimitiveType<?> primitive) {
		((PrimitiveType<Object>) primitive).setValue(null);
	}

	/**
	 * Returns the child of the given element described by the segment, creating it if it does not exist.
	 *
	 * @param parent  The parent element.
	 * @param segment The segment, e.g. {@code name[1]} or {@code valueQuantity}.
	 * @return The child element.
	 */
	private Base getOrCreateChild(final Base parent, final String segment) {
		final Matcher matcher = SEGMENT_PATTERN.matcher(segment);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid path segment '" + segment + "'");
		}
		final String name = matcher.group(1);
		final int index = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : -1;

		final ResolvedProperty property = resolveProperty(parent, name);
		final String propertyName = property.propertyName();

		if (property.choiceType() != null) {
			final List<Base> existing = parent.getNamedProperty(propertyName).getValues();
			if (!existing.isEmpty() && existing.get(0) != null &&
			    property.choiceType().equals(existing.get(0).fhirType())) {
				return existing.get(0);
			}

			final BaseRuntimeElementDefinition<?> definition = fhirContext.getElementDefinition(property.choiceType());
			if (definition == null) {
				throw new IllegalArgumentException("Unknown FHIR type '" + property.choiceType() + "'");
			}
			final Base child = (Base) definition.newInstance();
			parent.setProperty(propertyName, child);
			return child;
		}

		if (index >= 0) {
			while (parent.getNamedProperty(propertyName).getValues().size() <= index) {
				parent.makeProperty(propertyName.hashCode(), propertyName);
			}
			return parent.getNamedProperty(propertyName).getValues().get(index);
		}

		return parent.makeProperty(propertyName.hashCode(), propertyName);
	}

	/**
	 * Resolves the segment name to the property of the parent.
	 * Names of choice elements like {@code valueQuantity} are resolved to the property {@code value[x]}
	 * and the type {@code Quantity}.
	 */
	private ResolvedProperty resolveProperty(final Base parent, final String name) {
		return propertyCache.computeIfAbsent(parent.fhirType() + "." + name, ignored -> {
			final List<Property> children = parent.children();
			for (final Property child : children) {
				if (child.getName().equals(name)) {
					return new ResolvedProperty(name, null);
				}
			}

			for (final Property child : children) {
				final String childName = child.getName();
				if (!childName.endsWith("[x]")) {
					continue;
				}

				final String baseName = childName.substring(0, childName.length() - 3);
				if (!name.startsWith(baseName) || name.length() == baseName.length()) {
					continue;
				}

				final String typeSuffix = name.substring(baseName.length());
				for (final String type : child.getTypeCode().split("\\|")) {
					final String trimmedType = type.trim();
					if (trimmedType.equalsIgnoreCase(typeSuffix)) {
						return new ResolvedProperty(childName, trimmedType);
					}

					// Open types like Extension.value[x] allow any datatype
					if (trimmedType.equals("*")) {
						final String resolvedType = resolveDatatype(typeSuffix);
						if (resolvedType != null) {
							return new ResolvedProperty(childName, resolvedType);
						}
					}
				}
			}

			throw new IllegalArgumentException("Unknown element '" + name + "' on " + parent.fhirType());
		});
	}

	/**
	 * Resolves the type part of a choice element name to the name of a FHIR datatype.
	 * Complex types are capitalized ({@code Quantity}), primitive types are not ({@code dateTime}).
	 *
	 * @param typeSuffix The type part of the element name, e.g. {@code Quantity} or {@code DateTime}.
	 * @return The FHIR datatype or null if there is no such datatype.
	 */
	@Nullable
	private String resolveDatatype(final String typeSuffix) {
		final String primitiveName = Character.toLowerCase(typeSuffix.charAt(0)) + typeSuffix.substring(1);
		for (final String candidate : List.of(typeSuffix, primitiveName)) {
			final BaseRuntimeElementDefinition<?> definition = fhirContext.getElementDefinition(candidate);
			if (definition != null && definition.getName().equals(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * A resolved property.
	 *
	 * @param propertyName The name of the property, e.g. {@code value[x]}.
	 * @param choiceType   The type for choice elements, e.g. {@code Quantity}, null otherwise.
	 */
	private record ResolvedProperty(String propertyName, @Nullable String choiceType) {
	}

	/**
	 * Sort key of a single path segment.
	 *
	 * @param position The position of the property in the parent's children, following the FHIR specification.
	 * @param name     The element name, distinguishing the types of choice elements.
	 * @param index    The index for repeating elements, -1 otherwise.
	 */
	private record SegmentKey(int position, String name, int index) {
		private static final Comparator<SegmentKey> ORDER = Comparator.comparingInt(SegmentKey::position)
		                                                              .thenComparing(SegmentKey::name)
		                                                              .thenComparingInt(SegmentKey::index);
	}

	/**
	 * Information about a column.
	 *
	 * @param key          The sort key of the path.
	 * @param fhirType     The FHIR datatype of the values.
	 * @param allowedCodes The codes allowed by a required binding, null if the values are not restricted.
	 */
	private record ColumnInfo(List<SegmentKey> key, String fhirType, @Nullable List<String> allowedCodes) {
		private static final Comparator<ColumnInfo> ORDER = (a, b) -> {
			final int length = Math.min(a.key.size(), b.key.size());
			for (int i = 0; i < length; i++) {
				final int result = SegmentKey.ORDER.compare(a.key.get(i), b.key.get(i));
				if (result != 0) {
					return result;
				}
			}
			return Integer.compare(a.key.size(), b.key.size());
		};
	}
}
