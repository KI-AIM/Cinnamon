import { Component } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
})
export class DataConfigurationComponent {

    constructor(
        public configuration: DataConfigurationService,
        private titleService: TitleService, 
    ) {
        this.titleService.setPageTitle("Data configuration"); 
    }

    
}
