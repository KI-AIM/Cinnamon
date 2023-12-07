import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';
import { DataConfigurationComponent } from './features/data-upload/pages/data-configuration/data-configuration.component';
import { DataValidationComponent } from './features/data-upload/pages/data-validation/data-validation.component';
import {LoginComponent} from "./core/components/login/login.component";
import {RegisterComponent} from "./core/components/register/register.component";

const routes: Routes = [
    {path: '', redirectTo: '/login', pathMatch: 'full'},
    {path: 'login', component: LoginComponent},
    {path: 'register', component: RegisterComponent},
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
