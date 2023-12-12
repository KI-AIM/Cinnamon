import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { LoginComponent } from "./pages/login/login.component";
import { RegisterComponent } from "./pages/register/register.component";
import { MatInputModule } from "@angular/material/input";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { SharedModule } from "src/app/shared/shared.module";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatCardModule } from "@angular/material/card";
import { AppRoutingModule } from "src/app/app-routing.module";

@NgModule({
	declarations: [LoginComponent, RegisterComponent],
	imports: [
		AppRoutingModule,
		CommonModule,
		FormsModule,
		MatButtonModule,
		MatCardModule,
		MatIconModule,
		MatInputModule,
		ReactiveFormsModule,
		SharedModule,
	],
})
export class AuthModule {}
