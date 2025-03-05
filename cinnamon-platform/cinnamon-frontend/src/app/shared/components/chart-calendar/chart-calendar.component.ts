import { Component, Input } from '@angular/core';
import { ChartComponent, Entries } from "../chart/chart.component";
import { EChartsOption } from "echarts";
import { DensityPlotData, StatisticsData } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";

@Component({
    selector: 'app-chart-calendar',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartCalendarComponent extends ChartComponent {
    @Input() data!: StatisticsData<DensityPlotData>;
    @Input() simple: boolean = false;
    @Input() syntheticSeriesLabel: string = "Synthetic";

    private readonly colors = ['#770000', '#007700']

    private minDate: number;
    private maxDate: number;

    constructor(
        statisticsService: StatisticsService,
    ) {
        super(statisticsService);
    }

    protected override afterChartInit() {
        this.chartInstances!.on('dataZoom', (event) => {
            //@ts-ignore
            const startPercent = event.start;
            //@ts-ignore
            const endPercent = event.end;


            const diff = this.maxDate - this.minDate;
            const start = this.minDate + Math.floor((startPercent / 100) * diff);
            const end = this.minDate + Math.floor((endPercent / 100) * diff);
            const startDate = new Date(start);
            const endDate = new Date(end);

            // const totalDays = 365;
            // const startDate = new Date(2024, 0, 1 + Math.floor((startPercent / 100) * totalDays));
            // const endDate = new Date(2024, 0, 1 + Math.floor((endPercent / 100) * totalDays));

            //@ts-ignore
            this.options.calendar.range = [
                startDate.toISOString().split('T')[0],
                endDate.toISOString().split('T')[0],
            ];
            this.chartInstances.setOption(this.options);
        });
    }

    protected override afterZoomInit() {
        this.zoomInstance!.on('dataZoom', (event) => {
            //@ts-ignore
            const startPercent = event.start;
            //@ts-ignore
            const endPercent = event.end;


            const diff = this.maxDate - this.minDate;
            const start = this.minDate + Math.floor((startPercent / 100) * diff);
            const end = this.minDate + Math.floor((endPercent / 100) * diff);
            const startDate = new Date(start);
            const endDate = new Date(end);

            //@ts-ignore
            this.options.calendar.range = [
                startDate.toISOString().split('T')[0],
                endDate.toISOString().split('T')[0],
            ];
            this.chartInstances.setOption(this.options);
        });
    }

    protected override createChartOptions(): EChartsOption {
        const dataSetLabels: StatisticsData<string> = {
            real: "Original",
            synthetic: this.syntheticSeriesLabel,
        }

        let zoomSeries = [];
        let series = [];
        let max = 0;
        this.minDate = Number.MAX_VALUE;
        this.maxDate = Number.MIN_VALUE;
        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<DensityPlotData>>) {
            const dates: { [date: string]: number } = {};
            value.x_values.forEach(v => {
                if (typeof v === "string") {
                    v = Date.parse(v);
                }

                const date = new Date(v).toISOString().split('T')[0];
                if (dates[date]) {
                    dates[date] = dates[date] + 1;
                } else {
                    dates[date] = 1;
                }

                max = Math.max(max, dates[date]);
                this.minDate = Math.min(this.minDate, v);
                this.maxDate = Math.max(this.maxDate, v);
            });

            series.push({
                color: [this.colors[value.color_index]],
                name: dataSetLabels[key],
                type: 'heatmap',
                coordinateSystem: 'calendar',
                data: Object.entries(dates),
            });

            zoomSeries.push({
                type: 'line',
                data: Object.entries(dates),
            });
        }

        const options: EChartsOption = {
            ...this.graphOptions(this.simple),
            visualMap: {
                show: false,
                min: 0,
                max: max,
            },
            calendar: {
                    range: [new Date(this.minDate).toISOString().split('T')[0], new Date(this.maxDate).toISOString().split('T')[0]],
                },
            tooltip: {
                position: 'top',
            },


            xAxis: {
                type: 'category',
                data: this.data.real.x_values,
                show: false,
            },
            yAxis: {
                type: 'value',
                show: false,
            },
            dataZoom: [
                {
                    type: 'slider',
                    xAxisIndex: 0,
                    filterMode: 'none',
                    labelFormatter: (value: number, valueStr: string) => {
                        return this.formatNumber(this.data.real.x_values[value], DataType.DATE);
                    } ,
                },
            ],


        };

        // @ts-ignore
        options.series = series;


        this.zoom = {
            grid: {
                left: this.simple ? 25 : 70,
                top: this.simple ? 0 : 20,
                right: 30,
                bottom: this.simple ? 20 : 50,
                // show: true
            },

            xAxis: {
                data: this.data.real.x_values,
                type: 'category',
                show: false,
                axisLabel: {
                    formatter: (value: string, index: number) => {
                        return this.formatNumber(value, DataType.DATE);
                    }
                },
            },
            yAxis: {
                show: false,
            },
            dataZoom: [
                {
                    type: 'slider',
                    // @ts-ignore
                    xAxisIndex: 0,
                    filterMode: 'none',
                    start: 0,
                    end: 100,
                    show: true,
                    labelFormatter: (value: number, valueStr: string) => {
                      return this.formatNumber(this.data.real.x_values[value], DataType.DATE);
                    } ,
                },
            ],
            series: zoomSeries,
        } as EChartsOption;

        return options;
    }

}
