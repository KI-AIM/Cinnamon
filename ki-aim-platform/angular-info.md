# Project directories
The application's code is located in the `src/app/` directory. The entry point of the application is `index.html`, which currently only adds the root component `<app-root>` under `src/app/app.component.*`. The root module `app.module.ts` adds all the necessary modules and defines global providers.

The application is structured into several sub-directories, including `core`, `shared`, `features`, `assets`, and `styles`. The rules for each of these directories can be found in the `ABOUT` files. They describe what kind of files should be located in the directory.

The `core` directory contains files that only need to be loaded once, such as root-scoped services, static components (navbar / footer), interceptors, etc.

The `shared` directory contains everything that should be lazy-loaded and is used by multiple other components. Components here should not have dependencies on anything else.

The `features` directory is used to structure the components based on specific features. Every feature subdirectory contains pages, components, services, models, etc. that are specific to the feature.

The `assets` directory contains all static resources for a webpage. PathLocationStrategies can help make files easily available.

The `styling` directory contains global stylesheets.

# Main App Layout
The layout of the application is built in the root module `app.component.html`. The navigation is located on the left and handles different page configurations through an object (`steps.ts`). The StateManagement component handles completed steps and dependencies between different steps to create hierarchical views. It also allows deactivating following pages after making changes in an earlier view.

The title is located on top and is handled by TitleService so that every shown view can set its own title.

The central view is changed by the router component depending on the URL. The router links the view components to a specific URL (from `app-routing.module.ts`):

```ts
const routes: Routes = [
    {path: '', redirectTo: '/login', pathMatch: 'full'},
    {path: 'login', component: LoginComponent},
    {path: 'register', component: RegisterComponent},
    {path: 'start', component: StartpageComponent, canActivate: [AuthGuard]},
    {path: 'upload', component: UploadFileComponent, canActivate: [AuthGuard]},
    {path: 'dataConfiguration', component: DataConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'dataValidation', component: DataValidationComponent, canActivate: [AuthGuard]},
    {path: 'anonymizationConfiguration', component: AnonymizationConfigurationComponent, canActivate: [AuthGuard]}
];
```

![page structure](/img/page-structure.png)

![highlighted page structure](/img/page-structure_highlighted.png)

# How to handle data models and communication between Front- and Backend

Data models are plain classes that only store information. They should be identical to the corresponding backend model/JSON from the backend. Passing model objects to other components should be done by creating a service that can store the object on a specific level and can be injected automatically. Here is an example of a data model:

Example: `data-set.ts`
```ts
export class DataSet {
	data: Array<Array<any>>; 
	dataConfiguration: DataConfiguration;
}
```

Example `DataSet.java`
```java
public class DataSet {

	@JsonIgnore
	final List<DataRow> dataRows;

	@Schema(description = "Metadata of the data")
	final DataConfiguration dataConfiguration;

	@ArraySchema(schema = @Schema(description = "Row of the data set.", implementation = DataRow.class))
	@JsonProperty("data")
	public final List<List<Object>> getData() {
		return dataRows.stream()
		               .map(dataRow -> dataRow.getData()
		                                      .stream()
		                                      .map(Data::getValue)
		                                      .toList())
		               .toList();
	}
}
```

Doing this allows the easy conversion between the type safe data classes and the JSON strings that are transferred between the Front- and Backend.

This is used in several different components of the application. An example can be found in `upload-file.component.html` in line 13:

```html
<div class="py-2">
    <button mat-raised-button color="primary" (click)="uploadFile()" [disabled]="!this.file">Confirm file and start configuration</button>
</div>
```

Here the `uploadFile()` function is called (from `upload-file.component.ts`):

```ts
uploadFile() {
	this.loadingService.setLoadingStatus(true); 

	if (this.file) {
		this.fileService.setFile(this.file);
		this.fileService.setFileConfiguration(this.fileConfiguration)

		this.dataService.estimateData(this.file, this.fileService.getFileConfiguration()).subscribe({
			next: (d) => this.handleUpload(d),
			error: (e) => this.handleError(e),
		});
	}
}
```

This function uses a service class (DataService - `data.service.ts`) that provides an Observable object from a HTTPClient in its `estimateData` function: 

```ts
estimateData(file: File, fileConfig: FileConfiguration): Observable<Object> {
	const formData = new FormData();

	formData.append("file", file);

	const fileConfigString = JSON.stringify(fileConfig);
	formData.append("fileConfiguration", fileConfigString);

	return this.httpClient.post(this.baseUrl + "/datatypes", formData);
}
```

By doing this we can perform asynchronous requests that do not freeze the application. If we need to prevent user input during this time we need to manually freeze the application e.g. with the already implemented loading screen:
```ts
//enable
this.loadingService.setLoadingStatus(true); 
//disable
this.loadingService.setLoadingStatus(false);
```

![loading image](/img/page-loading.png)

# Add new views
Every new view should also be a part of a feature. Every feature can have multiple different views that are necessary to complete a feature. If helpful, features can also be split into multiple sub-features (e.g. `feautre_a` & `feature_b`).
In general, as every view is a component, the view can also be associated with logic. However this logic should read from the view or change the view in some way. Every action that is not dependent on the view itself should be moved to a service class.

# Data persistence between views
There are two different ways that data persistence is handled in the current state of the platform.
The first possibility is already described with "How to handle data models and communication between Front- and Backend". That means that in order to persist data, it is sent to the backend and saved in the database. A separate endpoint in the backend needs to be existant for this to work.

The other possiblity involves the creation of simple data classes in the frontend. Their sole purpose is to store different attributes and offer getter and setter methods.

Example `file.service.ts`:
```ts
import { FileConfiguration, FileType } from "../../../shared/model/file-configuration";
import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from "../../../shared/model/csv-file-configuration";

export class FileService {
	file: File;
    fileConfiguration: FileConfiguration;

	constructor() {
		this.fileConfiguration = new FileConfiguration(
			FileType.CSV, 
			new CsvFileConfiguration(
				Delimiter.COMMA, 
				LineEnding.LF, 
				QuoteChar.DOUBLE_QUOTE, 
				true
			)
		);
	}

	public getFile(): File {
		return this.file;
	}

	public setFile(value: File) {
		this.file = value;
	}

	public getFileConfiguration(): FileConfiguration {
		return this.fileConfiguration;
	}

	public setFileConfiguration(value: FileConfiguration) {
		this.fileConfiguration = value;
	}
}
```
Additionally we can use Angular providers to establish the level for which the data should be persisted:

A provider on module level is added in said module:
Example `data-upload.module.ts`:
```ts

@NgModule({
	declarations: [
		//declarations
	],
	imports: [
		//imports
	],
	exports: [
		//exports
	],
	providers: [
		FileService
		//providers
	],
})
export class DataUploadModule {}
```
As you can see, inside the data upload module - the module definition for the data upload feature - the FileService is registered as a provider. That means everytime the module is imported and used, a new instance for the file service is created. Between all files inside the module this single instance is automatically injected by defining it in a constructor of a component:

Example `upload-file.component.ts`:

```ts
export class UploadFileComponent {
	// class attributes

	constructor(
		//Additional constructor parameters
		private fileService: FileService,
		//Additional constructor parameters
	) {
			//Constructor
	}
}
```

To achieve the same on the root application level, the provider has to registered in the root module.

# Managing and synchronizing configurations
Managing generic configurations can be done by using the [ConfigurationsService](ki-aim-frontend/src/app/shared/services/configuration.service.ts).
This service provides the utility to import/export configurations and automatically synchronizations with the backend.
In order to use the ConfigurationService, the configuration has to be registered beforehand by calling the `registerConfiguration(data: ConfigurationRegisterData)` function with a configured [ConfigurationRegisterData](/ki-aim-frontend/src/app/shared/model/configuration-register-data.ts) object.
The details are described in the documentation of the fields.
The configurations can be registered at application start by using the provider in the module declaration.
Below is an example from the [DataUploadModule](ki-aim-frontend/src/app/features/data-upload/data-upload.module.ts).

```ts
@NgModule({
    // ...
    providers: [
        // ...
        {
            // Calls the useFactory function when starting the application
            provide: APP_INITIALIZER,
            useFactory: (service: DataConfigurationService) => function () { return service.registerConfig(); },
            deps: [DataConfigurationService],
            multi: true,
        },
    ],
})
export class DataUploadModule {}
```

A registered configuration gets fetched when the page is loaded.
For this the auth guard [state-guard](ki-aim-frontend/src/app/core/guards/state.guard.ts) is used to trigger the request.
When exporting or importing a configuration, the configuration gets pushed to the backend.

# Working with configuration pages
The [ConfigurationPage](ki-aim-frontend/src/app/shared/components/configuration-page) component and the [AlgorithmService](ki-aim-frontend/src/app/shared/services/algorithm.service.ts) together provide the utility to display a form based on a given configuration definition.
Each step that uses this utility should have its own module.
See the [Synthetization](ki-aim-frontend/src/app/features/synthetization) as an example.
A minimal setup requires a module containing a component that includes the `ConfigurationPage` component and a service that extends the `AlgorithmService`.
The module declaration and the service are used to register the configuration as shown in the previous section.
Additionally, three functions have to be implemented in the service.

```ts
export abstract class AlgorithmService {
    /**
     * Name of the step. Must be equal to the name in Spring's application.properties.
     */
    abstract getStepName(): string;

    /**
     * Creates the YAML configuration object sent to the backend as well as to the external module.
     * @param arg The configuration from the form.
     * @param selectedAlgorithm The selected algorithm.
     */
    abstract createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object;

    /**
     * Extracts the form data and the algorithm name from the given configuration object.
     * @param arg The configuration object.
     * @param configurationName The key of the configuration.
     */
    abstract readConfiguration(arg: Object, configurationName: string): { config: Object, selectedAlgorithm: Algorithm };
    
    // ...
}
```

The component is simple and requires two things.
Inside the HTML the `ConfigurationPage` has to be included as follows.

```html
<app-configuration-page></app-configuration-page>
```
In the component's declarations, an implementation of the algorithm service has to be provided.

```ts
@Component({
    // ...
    providers: [
        {
            provide: AlgorithmService,
            useExisting: SynthetizationService
        },
    ]
})
export class SynthetizationConfigurationComponent {
    // ...
}
```

The `ConfiguraitonPage` automatically injects the provided service.

```ts
export class ConfigurationPageComponent implements OnInit {
    // ...
    constructor(
        protected readonly algorithmService: AlgorithmService,
        // ...
    ) {
    }
    
    // ...
}
```
## Internal workflow
The page fetches the step configuration from the API using the step name defined inside the algorithm service implementation.
Then the available algorithms are fetched and displayed in the selection input.
When selecting an algorithm, the definition is fetched and the form is created dynamically by using the ["dynamic form"](https://angular.dev/guide/forms/dynamic-forms) pattern.
For that, the form object and the corresponding HTML form are created with a matching structure.

## New input types
New form elements can be added by creating a new switch case inside [configuration-input.component.html](ki-aim-frontend/src/app/shared/components/configuration-input/configuration-input.component.html).
For this, a new value of [ConfigurationInputType](ki-aim-frontend/src/app/shared/model/configuration-input-type.ts) should be created.
In special cases, the function `setToDefault()` inside [configuration-input.component.ts](ki-aim-frontend/src/app/shared/components/configuration-input/configuration-input.component.ts) has to be adapted as well.
New validations can be added by modifying the `createFrom` method inside [configuration-form.component.ts](ki-aim-frontend/src/app/shared/components/configuration-form/configuration-form.component.ts) and by adding new `<mat-error>` elements to the input in [configuration-input.component.html](ki-aim-frontend/src/app/shared/components/configuration-input/configuration-input.component.html)
For complex inputs, the `createForm` method must be adapted as well to create the matching form object.

# Styling conventions
It is also possible to define component specific styles. Upon creation, every components creates its own stylesheet and links it to the class definition. If a styling should be limited to only the component and should not be applied to anything else these stylesheets should be used.
For more general styling the global stylesheets in `src/app/styles/..` should be used. Examples for such styling could be layout specifc rules, colors, reappearing elements (that are not the component itself), etc. Currently these are rules for viewsizes, the general layout, some reusable variables, colors and icons.
Additionally all stylesheets use `.less`. In order to apply rules to the angular material components, `src/app/styles/angular-material.scss` has to be used, as the specifc settings can only be overwritten with `.scss` stylesheets.

# Usage of Angular Material components
In general [the Angular material documentation](https://material.angular.io/components/categories) is a good place to look for examplse on how to use the components.

The Angular material components have to be used in a unique way and work differently from normal froms that were created with bootstrap.
Most of the time the Angular material components have their own selector, some of them are used as a directive:

Example for Input field from `attribute-configuration.component.ts`:

```html
<mat-form-field>
	<mat-label>Column name</mat-label>
	<input type="text" matInput [id]="'name_' + attrNumber" [(ngModel)]="column.name" appTrimValue required appNoSpaceValidator #name="ngModel" (change)="column.name = column.name.trim()" />
	<mat-error *ngIf="name.errors?.['required']">Name must not be blank</mat-error>
	<mat-error *ngIf="name.errors?.['noSpace']">Name must not contain spaces</mat-error>
</mat-form-field>
```
Both examples can be seen in the shown code snippet. Form inputs have to be included in a `<mat-form-field>` element. A normal input element is enriched by the `matInput` directive to transform it to an Angular material input. The angular directives can be used like they normally would.
Validation can be performed with already existing or custom validation directives like `appNoSpaceValidator` that is an implementation of the Validator interface (Example from `no-space-validator.directive.ts`):
```ts
@Directive({
  selector: '[appNoSpaceValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: NoSpaceValidatorDirective,
      multi: true,
    }
  ],
})
export class NoSpaceValidatorDirective implements Validator {

  constructor() { }

  validate(control: AbstractControl<any, any>): ValidationErrors | null {
    return noSpaceValidator()(control);
  }
}
```
The element is available with the model tag `#name="ngModel"` that can be used in the typescript class (Example from `attribute-configuration.component.ts`):
```ts
@ViewChild("name") nameInput: NgModel;
```

The validation is automatically performed when registering the element upon loading (Example from `attribute-configuration.component.ts`):
```ts
ngAfterViewInit(): void {
	this.nameInput.statusChanges?.subscribe(() => {
		this.onValidation.emit(this.nameInput.valid ?? true)
	});
}
```

The errors are then available in an object for the ViewChild name (example from `attribute-configuration.component.html`):
```html
name.errors?.['noSpace']
```
Where the name of the validation entry is defined in the validation function (Example from `no-space-validator.directive.ts`):
```ts
export function noSpaceValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      return (typeof control.value === 'string') && control.value.trim().includes(" ")
        ? { noSpace: { value: control.value } }
        : null
    }
}
```


For specific Angular material elements it can be useful to create custom components with dynamic text that reduces the amount of code everytime the element is used. This has been done for the `info-card.component.html` and the  `information-dialog.component.html`
