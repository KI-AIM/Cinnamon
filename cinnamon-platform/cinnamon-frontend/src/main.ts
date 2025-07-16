/// <reference types="@angular/localize" />

import 'reflect-metadata'; // Required by class-transformer
import { platformBrowserDynamic } from "@angular/platform-browser-dynamic";
import { AppModule } from "src/app/app.module";

platformBrowserDynamic()
    .bootstrapModule(AppModule)
    .catch((err) => console.error(err));
