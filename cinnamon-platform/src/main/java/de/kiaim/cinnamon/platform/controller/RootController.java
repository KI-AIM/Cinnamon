package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class RootController {

    @Value("${cinnamon.is-demo-instance}")
    private boolean isDemoInstance;

    @Value("${cinnamon.max-file-size}")
    private long maxFileSize;

    private final CinnamonConfiguration cinnamonConfiguration;

    public RootController(final CinnamonConfiguration cinnamonConfiguration) {
	    this.cinnamonConfiguration = cinnamonConfiguration;
    }

    @RequestMapping(method = RequestMethod.GET, path = "/")
    public String index() {
        return "index.html";
    }

    @ResponseBody
    @GetMapping(value = "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("isDemoInstance", isDemoInstance);
        config.put("maxFileSize", maxFileSize);
        config.put("passwordRequirements", cinnamonConfiguration.getPasswordRequirements());
        return config;
    }

}
