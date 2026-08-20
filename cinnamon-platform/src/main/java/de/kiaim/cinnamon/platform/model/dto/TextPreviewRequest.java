package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Request for previewing postprocessed text.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for previewing postprocessed text.")
@Data
public class TextPreviewRequest {

	/**
	 * The text to be postprocessed and previewed.
	 */
	@Schema(description = "The text to be postprocessed and previewed.")
	@NotNull
	private String text;

	/**
	 * The ID of the invitation associated with the text.
	 */
	@Schema(description = "The ID of the invitation associated with the text.")
	@Nullable
	private String invitationId;

	/**
	 * Whether to substitute the invitation with an example invitation if the invitation ID is not provided or invalid.
	 */
	@Schema(description = "Whether to substitute the invitation with an example invitation if the invitation ID is not provided or invalid.")
	private boolean substituteInvitation = false;

	/**
	 * The ID of the project associated with the text.
	 */
	@Schema(description = "The ID of the project associated with the text.")
	@Nullable
	private String projectId;

}
