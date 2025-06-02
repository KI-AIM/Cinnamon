package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.CinnamonInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "root", description = "Endpoints for providing frontend resources.")
public class RootController {

    @Value("${cinnamon.is-demo-instance}")
    private boolean isDemoInstance;

    @Value("${cinnamon.max-file-size}")
    private long maxFileSize;

    @Value("${cinnamon.version}")
    private String version;

    private final CinnamonConfiguration cinnamonConfiguration;

    public RootController(final CinnamonConfiguration cinnamonConfiguration) {
	    this.cinnamonConfiguration = cinnamonConfiguration;
    }

    @RequestMapping(method = RequestMethod.GET, path = "/")
    public String index() {
        return "index.html";
    }

    @Operation(summary = "Returns information about Cinnamon.",
               description = "Returns information about Cinnamon.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                         description = "Returns the Cinnamon information.",
                         content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                             schema = @Schema(implementation = CinnamonInfo.class)),
                                    @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
                                             schema = @Schema(implementation = CinnamonInfo.class))}),
    })
    @ResponseBody
    @GetMapping(value = "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public CinnamonInfo getConfig() {
        return new CinnamonInfo(isDemoInstance,maxFileSize, cinnamonConfiguration.getPasswordRequirements(), version);
    }

}
