package de.kiaim.cinnamon.model.spring;

import org.springframework.http.MediaType;

/**
 * Class for custom media Types that Spring does not provide.
 *
 * @author Daniel Preciado-Marquez
 */
public abstract class CustomMediaType {
	/**
	 * Custom media type value for YAML.
	 */
	public static final String APPLICATION_X_YAML_VALUE = "application/x-yaml";

	/**
	 * Custom media type for YAML.
	 */
	public static final MediaType APPLICATION_X_YAML = MediaType.parseMediaType(APPLICATION_X_YAML_VALUE);

	/**
	 * Custom media type value for ZIP files.
	 */
	public static final String APPLICATION_ZIP_VALUE = "application/zip";

	/**
	 * Custom media type for ZIP files.
	 */
	public static final MediaType APPLICATION_ZIP = new MediaType("application", "zip");

	/**
	 * Custom media type value for YAML.
	 */
	public static final String TEXT_YAML_VALUE = "text/yaml";

	/**
	 * Custom media type for YAML.
	 */
	public static final MediaType TEXT_YAML = MediaType.parseMediaType(TEXT_YAML_VALUE);
}
