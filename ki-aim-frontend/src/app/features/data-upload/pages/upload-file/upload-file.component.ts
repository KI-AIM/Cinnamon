import { Component, ElementRef, TemplateRef, ViewChild } from '@angular/core';
import { Steps } from 'src/app/core/enums/steps';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataService } from 'src/app/shared/services/data.service';
import { plainToClass } from 'class-transformer';
import { DataConfiguration } from '../../../../shared/model/data-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { FileService } from '../../services/file.service';

@Component({
    selector: 'app-upload-file',
    templateUrl: './upload-file.component.html',
    styleUrls: ['./upload-file.component.less'],
})
export class UploadFileComponent {
    Steps = Steps;

    @ViewChild("uploadErrorModal") errorModal: TemplateRef<NgbModal>; 
    @ViewChild('fileForm') fileInput: ElementRef;

    errorMessage = ""; 

    constructor(
        private titleService: TitleService, 
        public stateManagement: StateManagementService, 
        private dataService: DataService,
        public dataConfigurationService: DataConfigurationService,
        private router: Router, 
        private modalService: NgbModal,
        private fileService: FileService
    ) {
        this.titleService.setPageTitle("Upload data"); 
    }

    uploadFile(event: any) {
        const file:File = event.target.files[0];
        if (file) {
            this.fileService.setFile(file);
            
            this.dataService.estimateData(file).subscribe({
                next: (d) => this.handleUpload(d),
                error: (e) => this.handleError(e),
            });
        }
    }

    private handleUpload(data: Object) {
        this.dataConfigurationService.setDataConfiguration(plainToClass(DataConfiguration, data))
        this.router.navigateByUrl("/dataConfiguration");
        this.stateManagement.addCompletedStep(Steps.UPLOAD); 
    }

    private handleError(error: HttpErrorResponse) {
        this.errorMessage = error.message; 
        this.showErrorModal(error);
    }
    
    private showErrorModal(error: HttpErrorResponse) {
		this.modalService.open(this.errorModal, { ariaLabelledBy: 'modal-basic-title', backdrop: "static" })
    }

    closeErrorModal() {
        this.modalService.dismissAll(); 
        this.fileInput.nativeElement.value = ""; 
    }

}
