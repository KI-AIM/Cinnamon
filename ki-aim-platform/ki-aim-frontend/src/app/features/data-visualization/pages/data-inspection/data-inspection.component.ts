import { Component, OnInit } from '@angular/core';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { StatisticsService } from '../../services/statistics.service';
import { HistogramData } from 'src/app/shared/model/visualization/histogram-entry';
import { TransformationService } from 'src/app/shared/services/transformation.service';

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.css'],
})
export class DataInspectionComponent implements OnInit {
    data: HistogramData; 
    isLoading = true; 

    constructor(
        public transformationService: TransformationService,
        public dataService: DataService,
        public configuration: DataConfigurationService,
        public statisticsService: StatisticsService,
    ) {
    }

    ngOnInit() {
        var names = this.configuration.getConfigurationNames(); 

        this.statisticsService.fetchHistogramForColumns(names).subscribe({
            next: (d) => {
                var jsonData = JSON.parse(JSON.stringify(d)); 
                this.data = new HistogramData(jsonData);
                this.isLoading = false; 
            },
            error: (error) => console.error('Failed to fetch data:', error)
        });
    }
}
