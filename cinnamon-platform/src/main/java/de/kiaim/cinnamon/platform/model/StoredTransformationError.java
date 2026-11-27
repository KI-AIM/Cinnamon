package de.kiaim.cinnamon.platform.model;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of a stored transformation error.
 * Used instead of {@link de.kiaim.cinnamon.platform.model.entity.DataTransformationErrorEntity} when reading errors,
 * so a large number of errors can be loaded without creating managed entities.
 * The component names match the properties of the entity, so it can be used as a projection in repository queries.
 *
 * @param rowIndex      Index of the row in the data set.
 * @param columnIndex   Index of the column in the data set.
 * @param errorType     The type of the error.
 * @param originalValue The original value, null if it was null.
 * @author Daniel Preciado-Marquez
 */
public record StoredTransformationError(int rowIndex, int columnIndex, TransformationErrorType errorType,
                                        @Nullable String originalValue) {
}
