package de.kiaim.cinnamon.platform.config;

import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.util.MimeType;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * HTTP message decoder for YAML content, backed by a Jackson YAMLMapper.
 * The standard JacksonJsonDecoder only supports the JSON JsonMapper.
 *
 * @author Daniel Preciado-Marquez
 */
public class YamlDecoder extends AbstractJacksonDecoder<YAMLMapper> {

	public YamlDecoder(final YAMLMapper yamlMapper, final MimeType... mediaTypes) {
		super(yamlMapper, mediaTypes);
	}
}
