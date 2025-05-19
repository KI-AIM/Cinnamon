import { Component, ElementRef, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatButton } from "@angular/material/button";
import { SharedModule } from "../../../../shared/shared.module";
import { AsyncPipe, NgIf } from "@angular/common";
import { Observable } from "rxjs";
import { ProjectSettings } from "../../../../shared/model/project-settings";
import { TitleService } from "../../../../core/services/title-service.service";
import { ProjectConfigurationService } from "../../../../shared/services/project-configuration.service";
import { StatisticsResponse } from "../../../../shared/model/statistics";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import { ChartFrequencyComponent } from "../../../../shared/components/chart-frequency/chart-frequency.component";
import { HttpClient } from "@angular/common/http";

@Component({
    selector: 'app-report',
    imports: [
        MatButton,
        SharedModule,
        AsyncPipe,
        NgIf
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

    constructor(
        private readonly http: HttpClient,
        private projectConfigService: ProjectConfigurationService,
        private statisticsService: StatisticsService,
        titleService: TitleService,
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
        this.http.get("/app/assets/report.css", {responseType: 'text'}).subscribe({
            next: value => {
                this.doPrintReport(value);
            },
        });
    }

    private doPrintReport(styleString: string) {
        const mywindow = window.open('', 'PRINT', 'height=650,width=900,top=100,left=150');

        if (!mywindow) {
            return;
        }

        mywindow.document.write(`<html><head><title>Report</title>`);
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

        mywindow.document.close(); // necessary for IE >= 10
        mywindow.focus(); // necessary for IE >= 10*/

        mywindow.print();
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

}
