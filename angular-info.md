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

Here the `uploadFile()` function is called:

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

This function uses a service class (DataService) that provides an Oberservable object from a HTTPClient

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
