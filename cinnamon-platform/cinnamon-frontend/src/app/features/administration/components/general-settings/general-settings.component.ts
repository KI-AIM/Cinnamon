import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormControl } from "@angular/forms";
import { NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { AppConfig, AppConfigService } from "@shared/services/app-config.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { combineLatest, Observable, tap } from "rxjs";

/**
 * Component for general admin settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-general-settings',
  standalone: false,
  templateUrl: './general-settings.component.html',
})
export class GeneralSettingsComponent implements OnInit {

    protected pageData$: Observable<{
        appConfig: AppConfig,
    }>;

    protected imprintFormControl: FormControl<string | null>;

    constructor(
        private readonly appConfigService: AppConfigService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly notificationService: NotificationService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Administration - General Settings");
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            appConfig: this.appConfigService.appConfig$.pipe(
                tap((appConfig) => {
                    this.imprintFormControl = this.formBuilder.control(appConfig.imprint);
                }),
            ),
        });
    }

    /**
     * Resets the value of the imprint input to the currently saved one.
     */
    protected resetImprint(): void {
        const oldValue = this.appConfigService.appConfig?.imprint;
        if (oldValue != null) {
            this.imprintFormControl.patchValue(oldValue);
            this.imprintFormControl.markAsPristine();
        }
    }

    /**
     * Saves the current content of the imprint input into the backend and updates the currently loaded imprint.
     */
    protected saveImprint(): void {
        this.appConfigService.updateImprint(this.imprintFormControl.value).subscribe({
            next: () => {
                this.notificationService.addNotificationSuccess("Imprint updated successfully.");
            },
            error: e => {
                this.errorHandlingService.addError(e, "Failed to update imprint!");
            }
        });
    }
}
