import { Component, ElementRef, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { TechnicalEvaluationService } from "@features/technical-evaluation/services/technical-evaluation.service";
import { AlgorithmDefinition } from "@shared/model/algorithm-definition";
import { MetricImportanceDefinition, MetricSettings, ProjectSettings } from "@shared/model/project-settings";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import {
    catchError,
    debounceTime,
    distinctUntilChanged,
    filter,
    map,
    Observable,
    of,
    Subscription,
    switchMap,
    tap
} from "rxjs";
import { UserService } from "src/app/shared/services/user.service";

/**
 * Component for the project settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-project-settings',
    standalone: false,
    templateUrl: './project-settings.component.html',
    styleUrl: './project-settings.component.less'
})
export class ProjectSettingsComponent implements OnInit, OnDestroy {

    protected projectSettingsForm: FormGroup;
    protected pageData$: Observable<{settings: ProjectSettings, algorithmDefinition: AlgorithmDefinition}>;

    protected isMainOpen: boolean = true;
    protected deletionError: string | null = null;

    private updateSubscription: Subscription | null = null;

    @ViewChild('projectSettingsDialog') private dialogWrap: TemplateRef<any>;
    @ViewChild('main') private main: ElementRef<HTMLDivElement>;
    @ViewChild('deletionConfirmation') private deletionConfirmation: ElementRef<HTMLDivElement>;

    public constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly matDialog: MatDialog,
        private readonly projectSettingsService: ProjectConfigurationService,
        private readonly technicalEvaluationService: TechnicalEvaluationService,
        private readonly userService: UserService,
    ) {
    }

    public ngOnInit(): void {
        this.pageData$ = of(null).pipe(
            switchMap(_ => {
               return this.projectSettingsService.projectSettings2$;
            }),
            switchMap(value => {
                return this.technicalEvaluationService.algorithms.pipe(
                   map(algorithms => {
                      return {settings: value, algorithms: algorithms};
                   }),
                );
            }),
            switchMap(value => {
                return this.technicalEvaluationService.getAlgorithmDefinition(value.algorithms[0]).pipe(
                    map(algorithmDefinition => {
                        return {
                            settings: value.settings,
                            algorithms: value.algorithms,
                            algorithmDefinition: algorithmDefinition
                        };
                    }),
                );
            }),
            catchError((error: any, value) => {
                this.errorHandlingService.addError(error, "Failed to load metrics!");
                return value.pipe(
                   map(data => {
                      return {
                          settings: data.settings,
                          algorithms: data.algorithms,
                          algorithmDefinition: new AlgorithmDefinition()
                      }
                   }),
                );
            }),
            tap(value => {
                this.projectSettingsForm = this.createForm(value.settings);
            }),
            tap(_ => {
                this.updateSubscription = this.projectSettingsForm.valueChanges.pipe(
                    debounceTime(300),
                    distinctUntilChanged(),
                    filter(_ => {
                        return !this.projectSettingsForm.invalid;
                    }),
                    switchMap(value => {
                        return this.projectSettingsService.setProjectSettings(value);
                    }),
                ).subscribe({
                    error: value => {
                        this.errorHandlingService.addError(value, "Failed to save settings!");
                    },
                });
            }),
        );
    }

    public ngOnDestroy(): void {
        if (this.updateSubscription !== null) {
            this.updateSubscription.unsubscribe();
        }
    }

    /**
     * Opens the dialog.
     */
    public open(): void {
        this.matDialog.open(this.dialogWrap, {
            width: '60%'
        });
    }

    /**
     * Returns the project name.
     * @protected
     */
    protected get projectName(): string {
        return this.userService.getUser().email;
    }

    /**
     * Opens the confirmation dialog for deleting the project.
     * @protected
     */
    protected openDeletionConfirmation(): void {
        this.deletionConfirmation.nativeElement.scrollIntoView();
        this.isMainOpen = false;
    }

    /**
     * Opens the main menu.
     * @protected
     */
    protected openMain(): void {
        this.main.nativeElement.scrollIntoView();
        this.isMainOpen = true;
    }

    /**
     * Deletes the project.
     * Displays a message if an error happens.
     *
     * @param projectName The name of the project for conformation.
     * @param password The password for confirmation.
     * @protected
     */
    protected deleteProject(projectName: string, password: string): void {
        this.deletionError = null;
        this.userService.delete(projectName, password).subscribe({
            next: () => {
                this.userService.logout('delete');
            },
            error: e => {
                this.deletionError = e.error.errorMessage;
            }
        });
    }

    /**
     * Creates the project settings form.
     * Initializes the form with the given values.
     *
     * @param settings The initial values.
     * @private
     */
    private createForm(settings: ProjectSettings): FormGroup {
        return this.formBuilder.group({
            projectName: [settings.projectName, [Validators.required]],
            contactMail: [settings.contactMail],
            contactUrl: [settings.contactUrl],
            reportCreator: [settings.reportCreator],
            metricConfiguration: this.createMetricForm(settings.metricConfiguration),
        });
    }

    /**
     * Creates the form for the metric settings.
     *
     * @param settings The initial values.
     * @private
     */
    private createMetricForm(settings: MetricSettings): FormGroup {
        return this.formBuilder.group({
            colorScheme: [settings.colorScheme, [Validators.required]],
            useUserDefinedImportance: [settings.useUserDefinedImportance],
            userDefinedImportance: this.createMetricImportanceForm(settings.userDefinedImportance),
        });
    }

    /**
     * Creates the metric importance form.
     *
     * @param settings The initial values.
     * @private
     */
    private createMetricImportanceForm(settings: MetricImportanceDefinition): FormGroup {
        const inputs: any = {};

        Object.entries(settings).forEach(([key, value]) => {
            inputs[key] = [{value: value, disabled: false}];
        });

        return this.formBuilder.group(inputs);
    }
}
