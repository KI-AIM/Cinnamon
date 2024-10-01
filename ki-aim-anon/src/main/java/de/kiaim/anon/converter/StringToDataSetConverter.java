package de.kiaim.anon.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.serialization.mapper.YamlMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToDataSetConverter implements Converter<String, DataSet> {

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    @Autowired
    public StringToDataSetConverter(final ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = YamlMapper.yamlMapper();
    }

    @Override
    @SneakyThrows
    public DataSet convert(String value) {
        if (value.startsWith("{")) {
            return jsonMapper.readValue(value, DataSet.class);
        } else {
            return yamlMapper.readValue(value, DataSet.class);
        }
    }

}
