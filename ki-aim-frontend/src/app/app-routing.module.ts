import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';

const routes: Routes = [
    {path: '', redirectTo: '/start', pathMatch: 'full'},
    {path: 'start', component: StartpageComponent},
    {path: 'upload', component: UploadFileComponent}
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { 
    
}
