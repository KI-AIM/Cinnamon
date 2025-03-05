package de.kiaim.cinnamon.model.spring;

import org.springframework.http.MediaType;

/**
 * Class for custom media Types that Spring does not provide.
 */
public abstract class CustomMediaType {
	/**
	 * Custom media type value for YAML.
	 */
	public static final String APPLICATION_YAML_VALUE = "application/x-yaml";

	/**
	 * Custom media type for YAML.
	 */
	public static final MediaType APPLICATION_YAML = new MediaType("application", "x-yaml");

	/**
	 * Custom media type value for ZIP files.
	 */
	public static final String APPLICATION_ZIP_VALUE = "application/zip";

	/**
	 * Custom media type for ZIP files.
	 */
	public static final MediaType APPLICATION_ZIP = new MediaType("application", "zip");
}
