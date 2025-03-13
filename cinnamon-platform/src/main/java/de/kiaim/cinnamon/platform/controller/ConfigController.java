package de.kiaim.cinnamon.platform.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("")
public class ConfigController {

	@Value("${cinnamon.is-demo-instance}")
	private boolean isDemoInstance;

	@GetMapping(value = "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> getConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("isDemoInstance", isDemoInstance);
		return config;
	}
}
