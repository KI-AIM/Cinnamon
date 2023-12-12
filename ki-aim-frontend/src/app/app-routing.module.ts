import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';
import { DataConfigurationComponent } from './features/data-upload/pages/data-configuration/data-configuration.component';
import { DataValidationComponent } from './features/data-upload/pages/data-validation/data-validation.component';
import {LoginComponent} from "./features/auth/pages/login/login.component";
import {RegisterComponent} from "./features/auth/pages/register/register.component";
import { AuthGuard } from './core/guards/auth.guard';

const routes: Routes = [
    {path: '', redirectTo: '/login', pathMatch: 'full'},
    {path: 'login', component: LoginComponent},
    {path: 'register', component: RegisterComponent},
    {path: 'start', component: StartpageComponent, canActivate: [AuthGuard]},
    {path: 'upload', component: UploadFileComponent, canActivate: [AuthGuard]},
    {path: 'dataConfiguration', component: DataConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'dataValidation', component: DataValidationComponent, canActivate: [AuthGuard]}
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule {

}
