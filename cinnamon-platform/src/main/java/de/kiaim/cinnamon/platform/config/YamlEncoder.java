package de.kiaim.cinnamon.platform.config;

import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.util.MimeType;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * HTTP message encoder for YAML content, backed by a Jackson YAMLMapper.
 * The standard JacksonJsonEncoder only supports the JSON JsonMapper.
 *
 * @author Daniel Preciado-Marquez
 */
public class YamlEncoder extends AbstractJacksonEncoder<YAMLMapper> {

	public YamlEncoder(final YAMLMapper yamlMapper, final MimeType... mediaTypes) {
		super(yamlMapper, mediaTypes);
	}
}
