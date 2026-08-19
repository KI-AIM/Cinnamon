package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
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
    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private final Set<FileType> compatibleFileTypes = new HashSet<>();

    /**
     * The resource types contained in the FHIR bundle.
     * Only relevant for files of type {@link de.kiaim.cinnamon.model.configuration.data.file.FileType#FHIR}
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    @Nullable
    private Set<String> fhirResourceTypes = null;

}
