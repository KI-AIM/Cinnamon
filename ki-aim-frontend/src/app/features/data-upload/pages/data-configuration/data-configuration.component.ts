import { Component } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { FileService } from '../../services/file.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { Steps } from 'src/app/core/enums/steps';

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
})
export class DataConfigurationComponent {

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService, 
        private titleService: TitleService, 
        private fileService: FileService,
        private router: Router,
        private stateManagement: StateManagementService,
        
    ) {
        this.titleService.setPageTitle("Data configuration"); 
    }

    confirmConfiguration() {
        this.dataService.readAndValidateData(this.fileService.getFile(), this.configuration.getDataConfiguration()).subscribe({
            next: (d) => this.handleUpload(d),
            error: (e) => this.handleError(e),
        });
    }

    private handleUpload(data: Object) {
        this.router.navigateByUrl("/dataValidation");
        this.stateManagement.addCompletedStep(Steps.DATA_CONFIG); 
    }

    private handleError(error: HttpErrorResponse) {
        //TODO implement me
    }
    
}
