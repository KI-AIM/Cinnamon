package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity class for the file metadata.
 * Attributes only depend on the file and file type.
 *
 * @author Daniel Preciado-Marquez
 */
@Embeddable
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class FileCompatibilityEntity {

    /**
     * The file types that are compatible with the file.
     */
    private final Set<FileType> compatibleFileTypes = new HashSet<>();

    /**
     * The resource types contained in the FHIR bundle.
     * Only relevant for files of type {@link de.kiaim.cinnamon.model.configuration.data.file.FileType#FHIR}
     */
    @Nullable
    private Set<String> fhirResourceTypes = null;

}
