package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Configurations specific for XLSX files.")
public class XlsxFileConfiguration {

    @Schema(description = "Whether the file contains a header row.", example = "true")
    private boolean hasHeader = true;

}
