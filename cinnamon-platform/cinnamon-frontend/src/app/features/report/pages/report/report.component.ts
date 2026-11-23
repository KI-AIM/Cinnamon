import { Platform } from "@angular/cdk/platform";
import { AsyncPipe, DecimalPipe, KeyValuePipe, LowerCasePipe, NgForOf, NgIf, NgOptimizedImage } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, ElementRef, Input, OnInit, QueryList, ViewChildren, ChangeDetectionStrategy } from '@angular/core';
import { MatButton } from "@angular/material/button";
import { ProcessStatus } from "@core/enums/process-status";
import { StateManagementService } from "@core/services/state-management.service";
import {
    AnonymizationAlgorithmData,
    AnonymizationService
} from "@features/anonymization/services/anonymization.service";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { ReportRenderService } from "@features/report/services/report-render.service";
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
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { ProjectService } from "@shared/services/project.service";
import { Color, StatisticsService } from "@shared/services/statistics.service";
import { SharedModule } from "@shared/shared.module";
import { catchError, combineLatest, firstValueFrom, map, Observable, of, switchMap, take, tap } from "rxjs";
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
        NgOptimizedImage,
    ],
    templateUrl: './report.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './report.component.less'
})
export class ReportComponent implements OnInit {

    private readonly PAGE_HEIGHT = 1122;

    /**
     * Maximum time to wait for charts to finish rendering in render mode.
     */
    private readonly CHART_RENDER_TIMEOUT_MS = 10_000;

    protected readonly CHART_BAR = 740;
    protected readonly CHART_MARGIN = 25;

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
     * If true, the report is rendered for the headless browser of the server side PDF generation.
     * Hides all controls and replaces the page with the printable report once everything is loaded.
     * Signals completion via `window.cinnamonReportStatus`.
     */
    @Input() public renderMode: boolean = false;
    private renderStarted = false;

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
        risks: RiskEvaluation | null,
        risksOriginal: RiskEvaluation | null,
        riskAssessmentConfig: RiskAssessmentConfig,
        statistics: StatisticsResponse,
        synthetizationConfig: AlgorithmData,
    } | null>;

    @ViewChildren('chart', {read: ElementRef}) protected chartDivs: QueryList<ElementRef<HTMLElement>>;
    @ViewChildren('chart') protected charts: QueryList<ChartFrequencyComponent>;

    constructor(
        private readonly anonymizationService: AnonymizationService,
        private readonly appConfigService: AppConfigService,
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly datasetInfoService: DataSetInfoService,
        private readonly errorHanldingService: ErrorHandlingService,
        private readonly http: HttpClient,
        protected readonly platform: Platform,
        private projectConfigService: ProjectConfigurationService,
        private readonly projectService: ProjectService,
        private readonly reportRenderService: ReportRenderService,
        private readonly riskAssessmentService: RiskAssessmentService,
        private readonly stateManagementService: StateManagementService,
        private statisticsService: StatisticsService,
        private readonly synthetizationService: SynthetizationService,
    ) {
        this.reportDate = new Date().toLocaleString();
    }

    ngOnInit() {
        // In render mode, the sources are tracked, so a stuck rendering reports which source never emitted
        const track = <T>(name: string, source: Observable<T>) => this.reportRenderService.track(name, source);

        this.pageData$ = combineLatest({
            anonymizationConfig: track("anonymization configuration", this.anonymizationService.getAlgorithmData$()),
            appConfig: track("app config", this.appConfigService.appConfig$),
            dataConfiguration: track("data configuration", this.dataConfigurationService.dataConfiguration$),
            datasetInfoOriginal: track("original dataset info", this.datasetInfoService.getDataSetInfo("VALIDATION")),
            datasetInfoProtected: track("protected dataset info", this.datasetInfoService.getDataSetInfo("PROTECTED")),
            mc: track("project settings", this.projectConfigService.projectSettings$),
            pipeline: track("pipeline", this.stateManagementService.pipelineInformation$),
            reportData: track("module report content", this.getReportData()),
            riskAssessmentConfig: track("risk assessment configuration", this.riskAssessmentService.fetchConfiguration().pipe(
                map(value => (value.config as any) as RiskAssessmentConfig),
            )),
            risks: track("risks", this.statisticsService.fetchRisks('RISK_EVALUATION').pipe(
                catchError(_ => {
                    return of(null);
                }),
            )),
            risksOriginal: track("risks of the original dataset", this.statisticsService.fetchRisks('RISK_EVALUATION_O').pipe(
                catchError(_ => {
                    return of(null);
                }),
            )),
            statistics: track("statistics", this.statisticsService.fetchResult()),
            synthetizationConfig: track("synthetization configuration", this.synthetizationService.getAlgorithmData$()),
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
            tap(() => {
                // pageData$ can emit multiple times, e.g. if the pipeline status updates
                if (this.renderMode && !this.renderStarted) {
                    this.renderStarted = true;
                    // Wait for the view to be rendered with the data
                    setTimeout(() => this.renderForHeadlessBrowser());
                }
            }),
            catchError(error => {
                if (this.renderMode) {
                    this.reportRenderService.fail("Failed to load the report data.", error);
                } else {
                    this.errorHanldingService.addError(error, "Failed to load report data");
                }
                return of(null);
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
        const chartImages = this.collectChartImages();
        const printableReport = this.getPrintableReport();

        this.loadPrintStyle().subscribe({
            next: value => {
                const mywindow = window.open('_', 'PRINT', 'height=650,width=900,top=100,left=150');
                if (!mywindow) {
                    return;
                }

                this.writePrintDocument(mywindow.document, printableReport, value.style, value.appConfig, chartImages);

                // Required to load the images
                setTimeout(() => {
                    mywindow.document.close(); // necessary for IE >= 10
                    mywindow.focus(); // necessary for IE >= 10*/

                    mywindow.print();
                }, 0);
            }
        });
    }

    /**
     * Replaces the current page with the printable report for the headless browser of the server side PDF generation.
     * The resulting document is identical to the one opened by {@link printReport}.
     * Signals the result via the {@link ReportRenderService}, which Gotenberg waits for.
     */
    private async renderForHeadlessBrowser(): Promise<void> {
        const render = this.reportRenderService;

        try {
            // Charts must be rendered completely before converting them into images
            await render.step("charts", () => this.waitForCharts());
            const chartImages = this.collectChartImages();
            const value = await render.step("report style sheet", () => firstValueFrom(this.loadPrintStyle()));

            // Replace the Angular app with the printable report.
            // The report must be read beforehand, as opening the document clears it.
            const printableReport = this.getPrintableReport();
            document.open();
            this.writePrintDocument(document, printableReport, value.style, value.appConfig, chartImages, false);
            document.close();

            // Wait for the chart images so the page splitting uses the final heights
            await render.step("chart images", () => Promise.all(
                Array.from(document.images).map(image => image.decode().catch(() => {
                    render.recordProblem(`Failed to decode image ${image.src.substring(0, 50)}`);
                })),
            ));
            this.splitOversizedElements(document);

            render.markReady();
        } catch (error) {
            await render.fail("Failed to create the printable report.", error);
        }
    }

    /**
     * Waits until all charts are rendered.
     * Charts that do not report their completion in time are converted anyway,
     * as a missing completion event should not block the entire report.
     * @private
     */
    private async waitForCharts(): Promise<void> {
        const charts = this.charts.toArray();
        let finished = 0;

        const timeout = new Promise<'timeout'>(resolve =>
            setTimeout(() => resolve('timeout'), this.CHART_RENDER_TIMEOUT_MS));
        const allRendered = Promise.all(charts.map(chart => chart.whenRendered().then(() => finished++)));

        if (await Promise.race([allRendered, timeout]) === 'timeout') {
            this.reportRenderService.recordProblem(
                `Only ${finished} of ${charts.length} charts reported their completion within ` +
                `${this.CHART_RENDER_TIMEOUT_MS / 1000}s; converting the remaining ones as they are.`);
        }
    }

    /**
     * Loads the style sheet of the report and replaces the contained variables.
     * @private
     */
    private loadPrintStyle(): Observable<{ style: string, appConfig: AppConfig }> {
        return combineLatest({
            style: this.http.get("/app/assets/report.css", {responseType: 'text'}),
            appConfig: this.appConfigService.appConfig$,
            projectSettings: this.projectConfigService.projectSettings2$,
        }).pipe(
            take(1),
            map(value => ({
                style: this.preprocessStyle(value.style, value.appConfig, value.projectSettings),
                appConfig: value.appConfig,
            })),
        );
    }

    /**
     * Assigns IDs to all charts and converts them into images.
     * Must be called while the charts are still part of the DOM.
     * @private
     */
    private collectChartImages(): ChartImage[] {
        const images: ChartImage[] = [];

        for (let i = 0; i < this.chartDivs.length; i++) {
            const chart = this.charts.get(i);
            const chartDiv = this.chartDivs.get(i);

            if (chart == null || chartDiv == null || chart.dataUrl == null) {
                continue;
            }

            const id = "chart" + i;
            chartDiv.nativeElement.id = id;
            images.push({
                id: id,
                dataUrl: chart.dataUrl,
                width: chartDiv.nativeElement.getBoundingClientRect().width,
            });
        }

        return images;
    }

    /**
     * Returns the HTML of the rendered report, prepared for the printable document.
     * Must be called before writing the printable document, as writing into the current document removes the report.
     * <p>
     * The printable document has neither the `<base>` element nor the icons of the app,
     * so relative image URLs would resolve against the URL of the page (e.g. `/report-render/...`)
     * and the browser would request the non-existing `/favicon.ico`.
     * Therefore, image URLs are made absolute and the icon links are copied.
     * A `<base>` element is not used, as it would break the anchor links of the report.
     * @private
     */
    private getPrintableReport(): PrintableReport {
        const report = document.getElementById("report");
        if (report == null) {
            throw new Error("The report element is not rendered.");
        }

        // The clone belongs to the current document, so URLs are still resolved against its base
        const clone = report.cloneNode(true) as HTMLElement;
        clone.querySelectorAll("img").forEach(image => {
            image.setAttribute("src", image.src);
            image.removeAttribute("srcset");
        });

        const iconLinks = Array.from(document.querySelectorAll<HTMLLinkElement>("link[rel~='icon']"))
            .map(link => `<link rel="${link.rel}" href="${link.href}">`)
            .join("");

        return {html: clone.innerHTML, head: iconLinks};
    }

    /**
     * Writes the printable report into the given document.
     *
     * @param target The document to write into, e.g. the one of a new window.
     * @param report The prepared report, see {@link getPrintableReport}.
     * @param styleString The preprocessed style sheet.
     * @param appConfig The app config.
     * @param chartImages The charts converted into images.
     * @param splitElements If oversized elements should be split directly.
     *                      Should be false if the layout is not final yet, e.g. because images are still loading.
     * @private
     */
    private writePrintDocument(target: Document, report: PrintableReport, styleString: string, appConfig: AppConfig,
                               chartImages: ChartImage[], splitElements: boolean = true): void {
        target.write(`<html><head><title>Privacy-Report by Cinnamon ${appConfig.version}</title>`);
        target.write(report.head);
        target.write(`<style>${styleString}</style>`);
        target.write('</head><body >');
        target.write(report.html);
        target.write('</body></html>');

        // Fix links for PDF
        target.querySelectorAll("a.report-anchor-link").forEach(a => {
            a.getAttributeNode("href")!.value = '#' + a.getAttributeNode("href")!.value.split("#")[1];
        });

        // Convert charts to images
        for (const chartImage of chartImages) {
            const newChart = target.getElementById(chartImage.id);
            if (newChart == null) {
                continue;
            }

            const image = target.createElement("img");
            image.src = chartImage.dataUrl;
            image.width = chartImage.width;

            newChart.style.display = 'none';

            // Center images again because of potential page margin changes
            const chartWrapper = target.createElement("div");
            chartWrapper.style.width = '100%';
            chartWrapper.style.display = 'flex'
            chartWrapper.style.justifyContent = 'center'
            chartWrapper.appendChild(image);

            newChart.parentElement!.insertBefore(chartWrapper, newChart);
        }

        if (splitElements) {
            this.splitOversizedElements(target);
        }
    }

    /**
     * Splits elements that do not fit on one page.
     * @param target The document containing the report.
     * @private
     */
    private splitOversizedElements(target: Document): void {
        target.querySelectorAll(".report-keep-together").forEach(e => {
            if (e.clientHeight > this.PAGE_HEIGHT) {
                // Allow page breaks in big elements to prevent bit gaps.
                // e.classList.remove("report-keep-together");

                // Split table into multiple tables
                this.breakTable(e as HTMLElement);
            }
        });
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

    protected offsetText(value: number): number {
        return this.clamp(this.calculatePos(value) - 60, 10, 620);
    }

    protected calculatePos(percentage: number) {
        return (percentage * this.CHART_BAR) + this.CHART_MARGIN;
    }

    private clamp(value: number, min: number, max: number): number {
        return Math.min(Math.max(value, min), max);
    }

    protected getColors(value: string): Color[] {
        return this.statisticsService.getColorSchemeGradient(value);
    }

    private baseUrl(projectId: string): string {
        return `${environments.apiUrl}/api/project/${projectId}/report`;
    }

    private getReportData(): Observable<ReportData> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.fetchReportData(projectId)),
        );
    }

    private fetchReportData(projectId: string): Observable<ReportData> {
        return this.http.get<ReportData>(this.baseUrl(projectId));
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

/**
 * The report prepared for writing it into the printable document.
 */
interface PrintableReport {
    /**
     * HTML of the report.
     */
    html: string;
    /**
     * Additional elements for the head of the printable document.
     */
    head: string;
}

/**
 * A chart of the report converted into an image.
 */
interface ChartImage {
    id: string;
    dataUrl: string;
    width: number;
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
