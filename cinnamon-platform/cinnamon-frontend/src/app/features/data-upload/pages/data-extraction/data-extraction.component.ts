import { Component, ViewChild } from "@angular/core";
import { Mode } from "@core/enums/mode";
import { AbstractControl, FormArray, FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { FileUploadComponent } from "@shared/components/file-upload/file-upload.component";
import { Steps } from "@core/enums/steps";
import { StateManagementService } from "@core/services/state-management.service";
import { NotificationService } from "@core/services/notification.service";
import { DataType, DataTypeMetadata } from "@shared/model/data-type";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { DataSetInfoService } from "../../services/data-set-info.service";
import { StatusService } from "@shared/services/status.service";
import { catchError, combineLatest, map, Observable, of, switchMap, takeWhile, timer } from "rxjs";
import {
    DataExtractionConfiguration,
    DataExtractionService,
    ExtractionField,
    LlmProfile,
    TextExtractionState,
} from "../../services/data-extraction.service";

@Component({
    selector: "app-data-extraction",
    templateUrl: "./data-extraction.component.html",
    styleUrls: ["./data-extraction.component.less"],
    standalone: false,
})
export class DataExtractionComponent {
    @ViewChild("configurationUpload") private configurationUpload: FileUploadComponent;

    protected readonly Mode = Mode;
    protected readonly Steps = Steps;
    protected readonly primitiveExtractionTypes = Object.entries(DataTypeMetadata)
        .filter(([type, metadata]) => ![DataType.UNDEFINED, DataType.TEXT].includes(type as DataType)
            && metadata.selectable)
        .map(([type, metadata]) => ({value: type.toLowerCase(), label: metadata.displayName}));
    protected readonly extractionTypes = [
        ...this.primitiveExtractionTypes,
        {value: "object", label: "Object"},
    ];
    protected readonly form: FormGroup;
    protected readonly profiles$: Observable<LlmProfile[]>;
    protected readonly textColumns$: Observable<string[]>;
    protected profilesLoadingFailed = false;
    protected extractionFailed = false;
    protected extractionError: string | null = null;
    protected saving = false;
    protected wideFormat = this.dataExtractionService.wideFormat;
    protected extractionEnabled = this.dataExtractionService.extractionEnabled;
    protected readonly pageData$;
    protected result = this.dataExtractionService.result;

    constructor(
        private readonly stateManagementService: StateManagementService,
        private readonly dataExtractionService: DataExtractionService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly notificationService: NotificationService,
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly dataSetInfoService: DataSetInfoService,
        statusService: StatusService,
        formBuilder: FormBuilder,
    ) {
        const existing = this.dataExtractionService.configuration;
        this.form = formBuilder.group({
            profile: new FormControl(existing?.profile ?? "", {nonNullable: true, validators: [Validators.required]}),
            source_column: new FormControl(existing?.source_column ?? "", {
                nonNullable: true,
                validators: [Validators.required],
            }),
            fields: formBuilder.array([]),
        });
        for (const field of existing?.fields ?? []) {
            this.addField(field);
        }
        if (this.fields.length === 0) {
            this.addField();
        }

        this.profiles$ = this.dataExtractionService.getProfiles().pipe(
            catchError(() => {
                this.profilesLoadingFailed = true;
                return of([]);
            }),
        );
        this.textColumns$ = this.dataConfigurationService.dataConfiguration$.pipe(
            map(configuration => configuration.configurations
                .filter(column => column.type === DataType.TEXT)
                .map(column => column.name)),
        );
        this.pageData$ = combineLatest({
            status: statusService.statusNonNull$,
            locked: this.stateManagementService.currentStepLocked$,
        });
        if (!this.extractionEnabled) {
            this.form.disable({emitEvent: false});
        }
        this.loadState();
    }

    protected get fields(): FormArray {
        return this.form.get("fields") as FormArray;
    }

    protected addField(
        value: Partial<ExtractionField> = {},
        target: FormArray = this.fields,
        nested = false,
    ): void {
        const type = new FormControl(value.type ?? "string", {
            nonNullable: true,
            validators: [Validators.required],
        });
        const format = new FormControl(value.format ?? "");
        const nestedFields = new FormArray<FormGroup>([]);
        const field = new FormGroup({
            name: new FormControl(value.name ?? "", [
                Validators.required,
                Validators.pattern(/^[A-Za-z_][A-Za-z0-9_]*$/),
            ]),
            type,
            description: new FormControl(value.description ?? "", [Validators.required]),
            allowed_values: new FormControl((value.allowed_values ?? []).join(", ")),
            min_value: new FormControl(value.min_value ?? ""),
            max_value: new FormControl(value.max_value ?? ""),
            format,
            fields: nestedFields,
        });
        for (const nestedField of value.fields ?? []) {
            this.addField(nestedField, nestedFields, true);
        }
        const updateTypeValidators = (selectedType: string): void => {
            format.setValidators(["date", "date_time"].includes(selectedType) ? [Validators.required] : []);
            format.updateValueAndValidity({emitEvent: false});
            if (this.isStructuredType(selectedType) && nestedFields.length === 0) {
                this.addField({}, nestedFields, true);
            }
            if (this.isStructuredType(selectedType) && !nested) {
                nestedFields.setValidators([Validators.minLength(1)]);
                nestedFields.enable({emitEvent: false});
            } else {
                nestedFields.clearValidators();
                nestedFields.disable({emitEvent: false});
            }
            nestedFields.updateValueAndValidity({emitEvent: false});
        };
        updateTypeValidators(type.value);
        type.valueChanges.subscribe(updateTypeValidators);
        target.push(field);
    }

    protected isRangeType(type: string): boolean {
        return ["integer", "decimal"].includes(type);
    }

    protected isStructuredType(type: string): boolean {
        return type === "object";
    }

    protected nestedFields(field: AbstractControl): FormArray {
        return field.get("fields") as FormArray;
    }

    protected addNestedField(field: AbstractControl): void {
        this.addField({}, this.nestedFields(field), true);
    }

    protected removeNestedField(field: AbstractControl, index: number): void {
        this.nestedFields(field).removeAt(index);
    }

    protected removeField(index: number): void {
        this.fields.removeAt(index);
    }

    protected async uploadConfiguration(files: FileList | null): Promise<void> {
        if (!files?.length) {
            return;
        }
        try {
            const configuration = this.dataExtractionService.parseConfiguration(await files[0].text());
            this.form.patchValue({
                profile: configuration.profile,
                source_column: configuration.source_column,
            });
            this.fields.clear();
            configuration.fields.forEach(field => this.addField(field));
            this.dataExtractionService.configuration = configuration;
            this.result = null;
            this.dataExtractionService.result = null;
            this.configurationUpload.clearFile();
            this.notificationService.addNotificationSuccess("Successfully imported the text extraction configuration.");
        } catch (error) {
            this.errorHandlingService.addError(error, "Could not upload text extraction configuration.");
        }
    }

    protected extract(): void {
        if (!this.extractionEnabled || this.form.invalid || this.fields.length === 0) {
            this.form.markAllAsTouched();
            return;
        }

        this.saving = true;
        this.extractionFailed = false;
        this.extractionError = null;
        const rawConfiguration = this.form.getRawValue();
        const configuration: DataExtractionConfiguration = {
            profile: rawConfiguration.profile,
            source_column: rawConfiguration.source_column,
            fields: rawConfiguration.fields.map((field: any) => this.toExtractionField(field)),
        };
        this.dataExtractionService.extract(configuration).subscribe({
            next: result => {
                this.dataExtractionService.configuration = configuration;
                this.applyState(result);
                this.loadState();
            },
            error: () => {
                this.saving = false;
                this.extractionFailed = true;
            },
        });
    }

    private toExtractionField(field: any): ExtractionField {
        const result: ExtractionField = {
            name: field.name,
            type: field.type,
            description: field.description,
        };
        if (this.isStructuredType(field.type)) {
            result.fields = field.fields.map((nestedField: any) => this.toExtractionField(nestedField));
        } else if (field.type === "string") {
            result.allowed_values = field.allowed_values
                .split(",")
                .map((value: string) => value.trim())
                .filter(Boolean);
        } else if (this.isRangeType(field.type)) {
            if (field.min_value !== "") {
                result.min_value = field.min_value;
            }
            if (field.max_value !== "") {
                result.max_value = field.max_value;
            }
        } else if (["date", "date_time"].includes(field.type)) {
            result.format = field.format;
        }
        return result;
    }

    protected continue(): void {
        if (!this.extractionEnabled) {
            this.stateManagementService.setAndRouteToStep(Steps.VALIDATION).subscribe();
            return;
        }
        this.saving = true;
        this.dataExtractionService.apply(this.wideFormat).subscribe({
            next: configuration => {
                this.dataConfigurationService.setDataConfiguration(configuration);
                this.dataSetInfoService.invalidateCache();
                this.stateManagementService.setAndRouteToStep(Steps.VALIDATION).subscribe();
            },
            error: error => {
                this.saving = false;
                this.errorHandlingService.addError(error, "Could not add the extracted data to the dataset.");
            },
        });
    }

    protected setWideFormat(wide: boolean): void {
        this.wideFormat = wide;
        this.dataExtractionService.wideFormat = wide;
        this.loadState();
    }

    protected setExtractionEnabled(enabled: boolean): void {
        this.extractionEnabled = enabled;
        this.dataExtractionService.extractionEnabled = enabled;
        if (enabled) {
            this.form.enable({emitEvent: false});
        } else {
            this.form.disable({emitEvent: false});
        }
    }

    protected downloadCsv(): void {
        if (this.result === null) {
            return;
        }
        const url = URL.createObjectURL(new Blob(
            ["\uFEFF", this.dataExtractionService.createCsv(this.result)],
            {type: "text/csv;charset=utf-8"},
        ));
        const link = document.createElement("a");
        link.href = url;
        link.download = "text-extraction.csv";
        link.click();
        URL.revokeObjectURL(url);
    }

    private loadState(): void {
        timer(0, 2000).pipe(
            switchMap(() => this.dataExtractionService.getState(this.wideFormat)),
            takeWhile(state => state.status === "RUNNING", true),
        ).subscribe({
            next: state => this.applyState(state),
            error: () => {
                this.saving = false;
                this.form.enable({emitEvent: false});
                this.extractionFailed = true;
            },
        });
    }

    private applyState(state: TextExtractionState): void {
        if (this.extractionEnabled) {
            this.form.enable({emitEvent: false});
        }
        if (state.configuration !== null) {
            this.form.patchValue({
                profile: state.configuration.profile,
                source_column: state.configuration.source_column,
            });
            this.fields.clear();
            state.configuration.fields.forEach(field => this.addField(field));
            this.dataExtractionService.configuration = state.configuration;
        }

        this.saving = state.status === "RUNNING";
        this.extractionFailed = state.status === "FAILED";
        this.extractionError = state.error;
        this.result = state.result;
        this.dataExtractionService.result = state.result;
        if (this.saving) {
            this.form.disable({emitEvent: false});
        }
    }
}
