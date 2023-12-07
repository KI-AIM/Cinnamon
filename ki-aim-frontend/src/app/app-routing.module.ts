import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';
import { DataConfigurationComponent } from './features/data-upload/pages/data-configuration/data-configuration.component';
import { DataValidationComponent } from './features/data-upload/pages/data-validation/data-validation.component';

const routes: Routes = [
    {path: '', redirectTo: '/start', pathMatch: 'full'},
    {path: 'start', component: StartpageComponent},
    {path: 'upload', component: UploadFileComponent},
    {path: 'dataConfiguration', component: DataConfigurationComponent},
    {path: 'dataValidation', component: DataValidationComponent}
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { 
    
}
