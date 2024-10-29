import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {ColumnConfiguration} from "../../model/column-configuration";
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {MatDialog} from "@angular/material/dialog";
import type {EChartsOption} from "echarts";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent implements OnInit {
    @Input() public configuration!: ColumnConfiguration;

    protected catImage$: Observable<CatImageMetadata[]>;
    protected options: EChartsOption;

    constructor(
        private readonly httpClient: HttpClient,
        private matDialog: MatDialog,
    ) {
    }

    ngOnInit(): void {
        this.catImage$ = this.httpClient.get<CatImageMetadata[]>("https://api.thecatapi.com/v1/images/search");

        const xAxisData = [];
        const data1 = [];
        const data2 = [];

        for (let i = 0; i < 100; i++) {
            xAxisData.push('category' + i);
            data1.push((Math.sin(i / 5) * (i / 5 - 10) + i / 6) * 5);
            data2.push((Math.cos(i / 5) * (i / 5 - 10) + i / 6) * 5);
        }

        this.options = {
            legend: {
                data: ['bar', 'bar2'],
                align: 'left',
            },
            tooltip: {},
            xAxis: {
                data: xAxisData,
                silent: false,
                splitLine: {
                    show: false,
                },
            },
            yAxis: {},
            series: [
                {
                    name: 'bar',
                    type: 'bar',
                    data: data1,
                    animationDelay: idx => idx * 10,
                },
                {
                    name: 'bar2',
                    type: 'bar',
                    data: data2,
                    animationDelay: idx => idx * 10 + 100,
                },
            ],
            animationEasing: 'elasticOut',
            animationDelayUpdate: idx => idx * 5,
        };
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            disableClose: true,
            width: '60%'
        });
    }
}

class CatImageMetadata {
    id: string;
    url: string;
    width: number;
    height: number;
}
