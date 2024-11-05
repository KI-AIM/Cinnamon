import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {MatDialog} from "@angular/material/dialog";
import { ECharts, EChartsOption } from "echarts";
import { AttributeStatistics } from "../../model/statistics";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent implements OnInit {
    // @Input() public configuration!: ColumnConfiguration;
    @Input() public attributeStatistics!: AttributeStatistics;

    protected options: EChartsOption;
    private chartInstances: ECharts;

    constructor(
        private matDialog: MatDialog,
    ) {
    }

    ngOnInit(): void {
        if (this.attributeStatistics.plot.density) {

            this.options = {
                // legend: {
                //     data: ['bar', 'bar2'],
                //     align: 'left',
                //     top: 20,
                // },
                grid: {
                    left: 50,
                    top: 50,
                    right: 30,
                    bottom: 50,
                    borderWidth: 1,
                    borderColor: '#ccc',
                    show: true
                },
                tooltip: {},
                xAxis: {
                    data: this.attributeStatistics.plot.density.real.x_values,
                    silent: false,
                    splitLine: {
                        show: false,
                    },
                },
                yAxis: {},
                series: [
                    {
                        // name: 'bar',
                        type: 'line',
                        data: this.attributeStatistics.plot.density.real.x_values,
                    },
                ],
            };

        } else if (this.attributeStatistics.plot.frequency_count) {

        } else {

        }
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '60%'
        });
        this.downloadGraph();
    }

    protected onChartInit(event: ECharts) {
        this.chartInstances = event;
    }

    private downloadGraph() {
        const imageUrl = this.chartInstances.getDataURL({
            type: 'jpeg',
            pixelRatio: 2,
            backgroundColor: '#fff',
        });
        this.downloadImage(imageUrl);
    }

    private downloadImage(dataUrl: string): void {
        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = this.attributeStatistics.attributeName +  '-mean.jpeg';
        a.click();
    }
}
