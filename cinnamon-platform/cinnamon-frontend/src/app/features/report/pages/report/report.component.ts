import { AsyncPipe, NgForOf, NgIf } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, ElementRef, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatButton } from "@angular/material/button";
import { TitleService } from "@core/services/title-service.service";
import { ChartFrequencyComponent } from "@shared/components/chart-frequency/chart-frequency.component";
import { ProjectSettings } from "@shared/model/project-settings";
import { StatisticsResponse } from "@shared/model/statistics";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { Color, StatisticsService } from "@shared/services/statistics.service";
import { UserService } from "@shared/services/user.service";
import { SharedModule } from "@shared/shared.module";
import { map, Observable, switchMap } from "rxjs";
import { AppConfig, AppConfigService } from "src/app/shared/services/app-config.service";

@Component({
    selector: 'app-report',
    imports: [
        MatButton,
        SharedModule,
        AsyncPipe,
        NgIf,
        NgForOf
    ],
    templateUrl: './report.component.html',
    styleUrl: './report.component.less'
})
export class ReportComponent implements OnInit {
    private readonly PAGE_HEIGHT = 1122;

    protected metricConfig$: Observable<ProjectSettings>;
    protected statistics$: Observable<StatisticsResponse | null>;

    @ViewChildren('chart', {read: ElementRef}) protected chartDivs: QueryList<ElementRef<HTMLElement>>;
    @ViewChildren('chart') protected charts: QueryList<ChartFrequencyComponent>;


    // TODO take form statistics
    protected utilityScoreO = 1.0;
    protected utilityScoreP = 0.7;
    protected riskScoreO = 0.1;
    protected riskScoreP = 0.7;

    constructor(
        private readonly appConfigService: AppConfigService,
        private readonly http: HttpClient,
        private projectConfigService: ProjectConfigurationService,
        private statisticsService: StatisticsService,
        titleService: TitleService,
        private readonly userService: UserService,
    ) {
        titleService.setPageTitle("Report");
    }

    ngOnInit() {
        this.metricConfig$ = this.projectConfigService.projectSettings$;
        this.statistics$ = this.statisticsService.fetchResult();
    }

    /**
     * Prints the report as a PDF.
     *
     * Everything that is wider than 670 px will not be visible in the PDF.
     * Links must have a `href` in the form `report#<anchor-id>` and must have the class `report-anchor-link`.
     * All charts in the report a converted to image and must have the `#chart` tag and must have a unique ID.
     *
     * @author Daniel Preciado-Marquez
     */
    protected printReport(): void {

        this.http.get("/app/assets/report.css", {responseType: 'text'}).pipe(
            switchMap(value => {
               return this.appConfigService.appConfig$.pipe(
                   map((config: AppConfig) => {
                      return {style: this.preprocessStyle(value, config), config: config};
                   }),
               );
            }),
        ).subscribe({
            next: value => {
                this.doPrintReport(value.style, value.config);
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
            newChart!.parentElement!.insertBefore(image, newChart);
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

    private preprocessStyle(style: string, appConfig: AppConfig): string {
        style = style.replaceAll("{{version}}", appConfig.version);
        style = style.replaceAll("{{now}}", new Date().toLocaleString());
        style = style.replaceAll("{{project}}", this.userService.getUser().email);
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

    protected get utilityScoreOX(): number {
        return this.calculatePos(this.utilityScoreO);
    }

    protected get utilityScorePX(): number {
        return this.calculatePos(this.utilityScoreP);
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

    private calculatePos(percentage: number) {
        return (percentage * 740) + 20;
    }

    private clamp(value: number, min: number, max: number): number {
        return Math.min(Math.max(value, min), max);
    }

    protected getColors(value: string): Color[] {
        return this.statisticsService.getColorSchemeGradient(value);
    }

    protected readonly StatisticsService = StatisticsService;
}
