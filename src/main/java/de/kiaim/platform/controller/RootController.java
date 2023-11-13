package de.kiaim.platform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class RootController {

    @RequestMapping(method = RequestMethod.GET, path = "/")
    public String index() {
        return "index.html";
    }
}
