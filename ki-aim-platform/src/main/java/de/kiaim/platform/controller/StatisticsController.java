package de.kiaim.platform.controller;

import de.kiaim.model.data.DataSet;
import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.BadColumnNameException;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.statistics.HistogramData;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.platform.service.StatisticsService;
import de.kiaim.platform.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@Tag(name = "/api/statistics", description = "API for calculating statistical information. "
    + "Data is associated with the user of the request.")
public class StatisticsController {

    private final DatabaseService databaseService;
    private final ProjectService projectService;
    private final UserService userService;
    private final StatisticsService statisticsService;

    public StatisticsController(
        final DatabaseService databaseService,
        final ProjectService projectService,
        final UserService userService,
        final StatisticsService statisticsService
    ) {
        this.databaseService = databaseService;
        this.projectService = projectService;
        this.userService = userService;
        this.statisticsService = statisticsService;
    }

    @GetMapping(
        value = "/histogram",
        produces = {
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            CustomMediaType.APPLICATION_YAML_VALUE
        }
    )
    public ResponseEntity<Object> calculateHistogram(
        @Parameter(
            description = "Name of the configuration to be loaded.",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(implementation = String.class)
            ),
            required = true
        )
        @ParameterObject LoadDataRequest request,
        @AuthenticationPrincipal UserEntity requestUser
    ) throws InternalDataSetPersistenceException, BadColumnNameException,
        InternalIOException, BadDataSetIdException {

        final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
        final ProjectEntity project = projectService.getProject(user);

        List<String> columnNames = Arrays.stream(request.getColumns().split(",")).toList();
        final DataSet dataSet = databaseService.exportDataSet(project, columnNames, Step.VALIDATION);

        HistogramData result = this.statisticsService.calculateHistogram(dataSet);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
