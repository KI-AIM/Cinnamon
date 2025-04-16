import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
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

    protected metricConfig$: Observable<ProjectSettings>;
    protected statistics$: Observable<StatisticsResponse | null>;


    @ViewChild('chart', {read: ElementRef}) protected chartDiv: ElementRef<HTMLElement>;
    @ViewChild('chart') protected chart: ChartFrequencyComponent;

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

    protected printReport(): void {
        this.http.get("/app/assets/pdf.css", {responseType: 'text'}).subscribe({
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

        const image = document.createElement("img");
        image.src = this.chart.dataUrl!;
        image.width = 670;

        const id = this.chartDiv.nativeElement.id;
        const newChart = mywindow.document.getElementById(id);
        newChart!.style.display = 'none';
        newChart!.parentElement!.insertBefore(image, newChart);

        mywindow.document.close(); // necessary for IE >= 10
        mywindow.focus(); // necessary for IE >= 10*/

        mywindow.print();
    }

}
