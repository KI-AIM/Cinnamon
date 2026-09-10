import { provideZoneChangeDetection } from "@angular/core";
/// <reference types="@angular/localize" />

import 'reflect-metadata'; // Required by class-transformer
import { platformBrowserDynamic } from "@angular/platform-browser-dynamic";
import { AppModule } from "src/app/app.module";

platformBrowserDynamic()
    .bootstrapModule(AppModule, { applicationProviders: [provideZoneChangeDetection()], })
    .catch((err) => console.error(err));
