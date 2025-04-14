import { Component, OnInit } from '@angular/core';
import { MatButton } from "@angular/material/button";
import { SharedModule } from "../../../../shared/shared.module";
import { AsyncPipe, NgIf } from "@angular/common";
import { catchError, filter, Observable, of, switchMap, take, timer } from "rxjs";
import { ProjectSettings } from "../../../../shared/model/project-settings";
import { TitleService } from "../../../../core/services/title-service.service";
import { ProjectConfigurationService } from "../../../../shared/services/project-configuration.service";
import { StatisticsResponse } from "../../../../shared/model/statistics";
import { StatisticsService } from "../../../../shared/services/statistics.service";

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

    constructor(
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
        //@ts-ignore
        // document.querySelector("#report").contentWindow?.print();
        // window.print();


        const mywindow = window.open('', 'PRINT', 'height=650,width=900,top=100,left=150');

        if (!mywindow) {
            return;
        }

        mywindow.document.write(`<html><head><title>Report</title>`);
        mywindow.document.write('</head><body >');
        mywindow.document.write(document.getElementById("report")!.innerHTML);
        mywindow.document.write('</body></html>');

        mywindow.document.close(); // necessary for IE >= 10
        mywindow.focus(); // necessary for IE >= 10*/

        mywindow.print();
        // mywindow.close();
    }

}
