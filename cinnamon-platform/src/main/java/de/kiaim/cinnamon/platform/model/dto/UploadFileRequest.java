package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class UploadFileRequest {

	@Parameter(description = "File containing the data.",
	           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))

	@NotNull(message = "Data must be present!")
	private MultipartFile file;
}
