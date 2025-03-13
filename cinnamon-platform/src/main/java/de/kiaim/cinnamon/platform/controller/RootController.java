package de.kiaim.cinnamon.platform.controller;

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

    @RequestMapping(method = RequestMethod.GET, path = "/")
    public String index() {
        return "index.html";
    }

    @ResponseBody
    @GetMapping(value = "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("isDemoInstance", isDemoInstance);
        return config;
    }

}
