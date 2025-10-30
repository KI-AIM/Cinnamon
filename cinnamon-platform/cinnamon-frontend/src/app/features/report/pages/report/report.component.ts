import { AsyncPipe, DecimalPipe, KeyValuePipe, LowerCasePipe, NgForOf, NgIf } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, ElementRef, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatButton } from "@angular/material/button";
import { ProcessStatus } from "@core/enums/process-status";
import { StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import {
    AnonymizationAlgorithmData,
    AnonymizationService
} from "@features/anonymization/services/anonymization.service";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { RiskAssessmentService } from "@features/risk-assessment/services/risk-assessment.service";
import { SynthetizationService } from "@features/synthetization/services/synthetization.service";
import { ChartFrequencyComponent } from "@shared/components/chart-frequency/chart-frequency.component";
import {
    MetricTableType
} from "@shared/components/data-inspection-metric-table/data-inspection-metric-table.component";
import { AttributeProtectionMetadata } from "@shared/model/anonymization-attribute-config";
import { DataConfiguration } from "@shared/model/data-configuration";
import { DataScale } from "@shared/model/data-scale";
import { DataSetInfo } from "@shared/model/data-set-info";
import { DateFormatConfiguration } from "@shared/model/date-format-configuration";
import { DateTimeFormatConfiguration } from "@shared/model/date-time-format-configuration";
import { PipelineInformation } from "@shared/model/execution-step";
import { ProjectSettings } from "@shared/model/project-settings";
import { RangeConfiguration } from "@shared/model/range-configuration";
import { RiskAssessmentConfig } from "@shared/model/risk-assessment-config";
import { RiskEvaluation } from "@shared/model/risk-evaluation";
import { Statistics, StatisticsResponse, StatisticsValues } from "@shared/model/statistics";
import { StringPatternConfiguration } from "@shared/model/string-pattern-configuration";
import { AlgorithmData } from "@shared/services/algorithm.service";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { Color, StatisticsService } from "@shared/services/statistics.service";
import { SharedModule } from "@shared/shared.module";
import { combineLatest, map, Observable, of, switchMap } from "rxjs";
import { AppConfig, AppConfigService } from "src/app/shared/services/app-config.service";
import { environments } from "src/environments/environment";

/**
 * Component for generating and displaying the report.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-report',
    imports: [
        MatButton,
        SharedModule,
        AsyncPipe,
        NgIf,
        NgForOf,
        DecimalPipe,
        LowerCasePipe,
        KeyValuePipe,
    ],
    templateUrl: './report.component.html',
    styleUrl: './report.component.less'
})
export class ReportComponent implements OnInit {

    private readonly baseUrl: string = environments.apiUrl + "/api/report";
    private readonly PAGE_HEIGHT = 1122;

    protected readonly AttributeProtectionMetadata = AttributeProtectionMetadata;
    protected readonly DateFormatConfiguration = DateFormatConfiguration;
    protected readonly DateTimeFormatConfiguration = DateTimeFormatConfiguration;
    protected readonly MetricTableType = MetricTableType;
    protected readonly Object = Object;
    protected readonly ProcessStatus = ProcessStatus;
    protected readonly RangeConfiguration = RangeConfiguration;
    protected readonly StatisticsService = StatisticsService;
    protected readonly StringPatternConfiguration = StringPatternConfiguration;

    /**
     * Date of the report creation.
     * @protected
     */
    protected reportDate: string;

    /**
     * Asynchronous data for showing the page.
     * @protected
     */
    protected pageData$: Observable<{
        anonymizationConfig: AnonymizationAlgorithmData,
        appConfig: AppConfig,
        dataConfiguration: DataConfiguration,
        datasetInfoAnonymized: DataSetInfo | null,
        datasetInfoOriginal: DataSetInfo,
        datasetInfoProtected: DataSetInfo,
        numericalAttributes: NumericAttributes,
        mc: ProjectSettings,
        pipeline: PipelineInformation,
        reportData: ReportData,
        risks: RiskEvaluation,
        riskAssessmentConfig: RiskAssessmentConfig,
        statistics: StatisticsResponse,
        synthetizationConfig: AlgorithmData,
    }>;

    @ViewChildren('chart', {read: ElementRef}) protected chartDivs: QueryList<ElementRef<HTMLElement>>;
    @ViewChildren('chart') protected charts: QueryList<ChartFrequencyComponent>;

    // TODO take form statistics
    protected riskScoreO = 0.1;
    protected riskScoreP = 0.7;

    constructor(
        private readonly anonymizationService: AnonymizationService,
        private readonly appConfigService: AppConfigService,
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly datasetInfoService: DataSetInfoService,
        private readonly http: HttpClient,
        private projectConfigService: ProjectConfigurationService,
        private readonly riskAssessmentService: RiskAssessmentService,
        private readonly stateManagementService: StateManagementService,
        private statisticsService: StatisticsService,
        private readonly synthetizationService: SynthetizationService,
        titleService: TitleService,
    ) {
        titleService.setPageTitle("Report");
        this.reportDate = new Date().toLocaleString();
    }

    ngOnInit() {
        this.pageData$ = combineLatest({
            anonymizationConfig: this.anonymizationService.getAlgorithmData$(),
            appConfig: this.appConfigService.appConfig$,
            dataConfiguration: this.dataConfigurationService.dataConfiguration$,
            datasetInfoOriginal: this.datasetInfoService.getDataSetInfo("VALIDATION"),
            datasetInfoProtected: this.datasetInfoService.getDataSetInfo("PROTECTED"),
            mc: this.projectConfigService.projectSettings$,
            pipeline: this.stateManagementService.pipelineInformation$,
            reportData: this.fetchReportData(),
            riskAssessmentConfig: this.riskAssessmentService.fetchConfiguration().pipe(
                map(value => (value.config as any) as RiskAssessmentConfig),
            ),
            risks: this.statisticsService.fetchRisks(),
            statistics: this.statisticsService.fetchResult(),
            synthetizationConfig: this.synthetizationService.getAlgorithmData$(),
        }).pipe(
            switchMap(value => {
                // Fetch information about the anonymized dataset if anonymization was applied
                if (value.pipeline.stages[0].processes[0].externalProcessStatus === ProcessStatus.FINISHED) {
                    return this.datasetInfoService.getDataSetInfo("anonymization").pipe(
                        map(datasetInfoAnonymized => ({
                            ...value,
                            datasetInfoAnonymized: datasetInfoAnonymized,
                        })),
                    );
                } else {
                    return of({
                        ...value,
                        datasetInfoAnonymized: null,
                    })
                }
            }),
            map(value => {
                // Create the data for the table about the numerical attributes
               return {
                    ...value,
                   numericalAttributes: this.createNumericAttribute(value.statistics.statistics),
               };
            }),
        );
    }

    /**
     * Calculates the utility score of the original dataset.
     *
     * @param statistics The statistics of the technical evaluation.
     * @protected
     */
    protected getUtilityScoreOriginal(statistics: Statistics): number {
        const resemblance = statistics.Overview.aggregated_metrics.overall_resemblance.values.real
        const utility = statistics.Overview.aggregated_metrics.overall_utility.values.real
        return (resemblance + utility) / 2;
    }

    /**
     * Calculates the utility score of the protected dataset.
     *
     * @param statistics The statistics of the technical evaluation.
     * @protected
     */
    protected getUtilityScoreProtected(statistics: Statistics): number {
        const resemblance = statistics.Overview.aggregated_metrics.overall_resemblance.values.synthetic
        const utility = statistics.Overview.aggregated_metrics.overall_utility.values.synthetic
        return (resemblance + utility) / 2;
    }

    /**
     * Prints the report as a PDF.
     *
     * Everything that is wider than 670 px will not be visible in the PDF.
     * Links must have a `href` in the form `report#<anchor-id>` and must have the class `report-anchor-link`.
     * All charts in the report a converted to image and must have the `#chart` tag and must have a unique ID.
     */
    protected printReport(): void {
        // Preprocess the report
        // Assign IDs to all charts
        for (let i = 0; i < this.chartDivs.length; i++) {
            const chart = this.chartDivs.get(i);
            chart!.nativeElement.id = "chart" + i;
        }

        this.http.get("/app/assets/report.css", {responseType: 'text'}).pipe(
            switchMap(value => {
                return this.appConfigService.appConfig$.pipe(
                    map((config: AppConfig) => {
                        return {style: value, appConfig: config};
                    }),
                );
            }),
            switchMap(value => {
                return this.projectConfigService.projectSettings2$.pipe(
                    map((projectSettings: ProjectSettings) => {
                        return {
                            style: value.style,
                            appConfig: value.appConfig,
                            projectSettings: projectSettings,
                        };
                    }),
                )
            }),
            map(value => {
                return {
                    style: this.preprocessStyle(value.style, value.appConfig, value.projectSettings),
                    appConfig: value.appConfig
                };
            })
        ).subscribe({
            next: value => {
                this.doPrintReport(value.style, value.appConfig);
            }
        });
    }

    private doPrintReport(styleString: string, appConfig: AppConfig): void {
        const mywindow = window.open('_', 'PRINT', 'height=650,width=900,top=100,left=150');

        if (!mywindow) {
            return;
        }

        mywindow.document.write(`<html><head><title>Privacy-Report by Cinnamon ${appConfig.version}</title>`);
        mywindow.document.write(`<style>${styleString}</style>`);
        mywindow.document.write('</head><body >');
        mywindow.document.write(document.getElementById("report")!.innerHTML);
        mywindow.document.write('</body></html>');

        // Fix links for PDF
        mywindow.document.querySelectorAll("a.report-anchor-link").forEach(a => {
            a.getAttributeNode("href")!.value = '#' + a.getAttributeNode("href")!.value.split("#")[1];
        });

        // Convert charts to images
        for (let i = 0; i < this.chartDivs.length; i++) {
            const chart = this.charts.get(i);
            const chartDiv = this.chartDivs.get(i);

            if (chart == null || chartDiv == null) {
                continue;
            }

            const image = document.createElement("img");
            image.src = chart.dataUrl!;
            image.width = 670;

            const id = chartDiv.nativeElement.id;
            const newChart = mywindow.document.getElementById(id);
            newChart!.style.display = 'none';

            // Center images again because of potential page margin changes
            const chartWrapper = document.createElement("div");
            chartWrapper.style.width = '100%';
            chartWrapper.style.display = 'flex'
            chartWrapper.style.justifyContent = 'center'
            chartWrapper.appendChild(image);

            newChart!.parentElement!.insertBefore(chartWrapper, newChart);
        }

        // Handle elements that do not fit on one page
        mywindow.document.querySelectorAll(".report-keep-together").forEach(e => {
            if (e.clientHeight > this.PAGE_HEIGHT) {
                // Allow page breaks in big elements to prevent bit gaps.
                // e.classList.remove("report-keep-together");

                // Split table into multiple tables
                this.breakTable(e as HTMLElement);
            }
        });

        // Required to load the images
        setTimeout(() => {
            mywindow.document.close(); // necessary for IE >= 10
            mywindow.focus(); // necessary for IE >= 10*/

            mywindow.print();
        }, 0);
    }

    /**
     * Replaces variables in the CSS file.
     *
     * @param style The content of the style sheet.
     * @param appConfig The app config.
     * @param projectSettings The project settings.
     * @private
     */
    private preprocessStyle(style: string, appConfig: AppConfig, projectSettings: ProjectSettings): string {
        style = style.replaceAll("{{version}}", appConfig.version);
        style = style.replaceAll("{{now}}", this.reportDate);
        style = style.replaceAll("{{creator}}", projectSettings.reportCreator ?? "Unknown");
        style = style.replaceAll("{{project}}", projectSettings.projectName);
        return style;
    }

    /**
     * Splits the given table into multiple tables so that each new table fits on one page.
     * @param table The table to split.
     * @private
     */
    private breakTable(table : HTMLElement) {
        let tableStart = table.getBoundingClientRect().top;
        let newTable: HTMLElement | null = null;

        const oho = table.querySelectorAll(".report-table-row");

        for (let rowIndex = 0; rowIndex < oho.length; rowIndex++) {
            const row = oho[rowIndex];

            const rect = row.getBoundingClientRect();
            const pos = rect.top - tableStart;

            if (newTable != null) {
                // The row is in the new table, so remove it from the original table
                row.remove();
            } else if (pos > (this.PAGE_HEIGHT)) {
                row.id ="breakBefore";

                // Create a new table
                newTable = table.cloneNode(true) as HTMLElement;
                const description = newTable.querySelector(".report-table-description");
                if (description != null) {
                    description.classList.add("report-table-description-continued");
                    description.innerHTML = " Continued";
                }

                // Remove previous rows from the new table
                const newRows = newTable.querySelectorAll(".report-table-row");
                for (let i = 0; i < newRows.length; i++) {
                    const newRow = newRows[i];
                    if (newRow.id === "breakBefore") {
                        // Reset id for next split
                        newRow.id = "";
                        break;
                    }
                    newRow.remove();
                }

                // Insert table
                table.parentElement!.insertBefore(newTable, table.nextSibling);
                tableStart = newTable.getBoundingClientRect().top;

                // Remove row form original table
                row.remove();
            }
        }

        // Split the new table if it is still bigger than one page
        if (newTable != null && newTable.clientHeight > this.PAGE_HEIGHT) {
            this.breakTable(newTable);
        }
    }

    protected get riskScoreOX(): number {
        return this.calculatePos(this.riskScoreO);
    }

    protected get riskScorePX(): number {
        return this.calculatePos(this.riskScoreP);
    }

    protected offsetText(value: number): number {
        return this.clamp(this.calculatePos(value) - 60, 10, 640);
    }

    protected calculatePos(percentage: number) {
        return (percentage * 740) + 20;
    }

    private clamp(value: number, min: number, max: number): number {
        return Math.min(Math.max(value, min), max);
    }

    protected getColors(value: string): Color[] {
        return this.statisticsService.getColorSchemeGradient(value);
    }

    private fetchReportData(): Observable<ReportData> {
        return this.http.get<ReportData>(this.baseUrl);
    }

    /**
     * Creates data about the first 4 numerical attributes in the dataset.
     *
     * @param statistics Calculated statistics.
     * @private
     */
    private createNumericAttribute(statistics: Statistics): NumericAttributes {
        const firstNumericalAttributes = {
            first_attributes: [],
            prop_diff_mean: 0.0,

            number_all: 0,
            prop_diff_mean_all: 0.0,
        } as NumericAttributes;

        for (const attr of statistics.resemblance.attributes) {
            // Ignore non numerical attributes
            const type = attr.attribute_information?.scale;
            if (type !== DataScale.INTERVAL && type !== DataScale.RATIO) {
                continue;
            }

            // Ignore attributes with missing statistics
            const meanObj = attr.important_metrics['mean'];
            const stdObj = attr.important_metrics['standard_deviation'];

            if (!(meanObj instanceof StatisticsValues) || !(stdObj instanceof StatisticsValues)) {
                continue;
            }

            const mean = meanObj as StatisticsValues;
            const std = stdObj as StatisticsValues;

            // Add proportional difference for total calculation
            firstNumericalAttributes.number_all += 1;
            firstNumericalAttributes.prop_diff_mean_all += mean.difference.percentage;

            // Continue if 4 numerical attributes are found for the table
            if (firstNumericalAttributes.first_attributes.length == 4) {
                continue;
            }

            // Add the data of the attribute for the table
            firstNumericalAttributes.first_attributes.push({
                name: attr.attribute_information.name,
                original_mean: mean.values.real,
                original_std: std.values.real,
                protected_mean: mean.values.synthetic,
                protected_std: std.values.synthetic,
                abs_diff: mean.difference.absolute,
                prop_diff: mean.difference.percentage,
            });
        }

        // Calculate the mean proportional difference
        firstNumericalAttributes.prop_diff_mean_all /= firstNumericalAttributes.number_all;

        return firstNumericalAttributes;
    }
}

interface ReportData {
    [key: string]: ModuleReportContent | null;
}

interface ModuleReportContent {
    configDescription: string;
    glossar: string | null;
}

/**
 * Data for one row in the table of numerical attributes.
 */
interface NumericAttribute {
    name: string;
    original_mean: number;
    original_std: number;
    protected_mean: number;
    protected_std: number;
    abs_diff: number;
    prop_diff: number;
}

/**
 * Data for the table of numerical attributes.
 */
interface NumericAttributes {
    first_attributes: NumericAttribute[],

    number_all: number,
    prop_diff_mean_all: number
}
