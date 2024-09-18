import { Component, OnInit } from '@angular/core';
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';

@Component({
    selector: 'app-anonymization-attribute-configuration',
    templateUrl: './anonymization-attribute-configuration.component.html',
    styleUrls: ['./anonymization-attribute-configuration.component.css'],
})
export class AnonymizationAttributeConfigurationComponent implements OnInit {
    constructor(
        public configuration: DataConfigurationService
    ) {

    }

    dataConfiguration: DataConfiguration;

    ngOnInit() {
        this.dataConfiguration = this.configuration.getDataConfiguration(); 
    }

}
