package de.kiaim.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.serialization.mapper.JsonMapper;
import de.kiaim.model.serialization.mapper.YamlMapper;
import de.kiaim.platform.json.DataSetSerializer;
import de.kiaim.platform.service.DataSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializationConfig {

	private final DataSetService dataSetService;

	@Autowired
	public SerializationConfig(DataSetService dataSetService) {
		this.dataSetService = dataSetService;
	}

//	@Bean
	public ObjectMapper jsonMapper() {
		var jsonMapper = JsonMapper.jsonMapper();

		final SimpleModule module = new SimpleModule();
		module.addSerializer(DataSet.class, new DataSetSerializer(dataSetService));
		jsonMapper.registerModule(module);

		return jsonMapper;
	}

	@Bean
	public ObjectMapper yamlMapper() {
		var yamlMapper = YamlMapper.yamlMapper();

		final SimpleModule module = new SimpleModule();
		module.addSerializer(DataSet.class, new DataSetSerializer(dataSetService));

		yamlMapper.registerModule(module);

		return yamlMapper;
	}
}
