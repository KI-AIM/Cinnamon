import { Component, Input } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { SynthetizationService } from "../../../features/synthetization/services/synthetization.service";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { ErrorHandlingService } from "../../services/error-handling.service";

@Component({
    selector: 'app-configuration-input-named-list',
    templateUrl: './configuration-input-named-list.component.html',
    styleUrls: ['./configuration-input-named-list.component.less'],
    standalone: false
})
export class ConfigurationInputNamedListComponent {
    private static readonly SUGGESTABLE_LISTS = new Set([
        "required_attributes",
    ]);

    @Input() configurationInputDefinition!: ConfigurationInputDefinition;
    @Input() parentForm!: FormGroup;
    @Input() disabled!: boolean;
    protected suggesting = false;

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly notificationService: NotificationService,
        private readonly synthetizationService: SynthetizationService,
    ) {
    }

    get formArray(): FormArray {
        return this.parentForm.controls[this.configurationInputDefinition.name] as FormArray;
    }

    hasValues(): boolean {
        return this.formArray.length > 0;
    }

    addValue() {
        this.formArray.push(this.createItemGroup());
    }

    removeAllValues() {
        this.formArray.clear();
    }

    removeValue(index: number) {
        this.formArray.removeAt(index);
    }

    canSuggestValues(): boolean {
        return ConfigurationInputNamedListComponent.SUGGESTABLE_LISTS.has(this.configurationInputDefinition.name);
    }

    suggestValues() {
        if (this.disabled || this.suggesting || !this.canSuggestValues()) {
            return;
        }

        this.suggesting = true;
        const rootValue = (this.parentForm.root as FormGroup).getRawValue();

        const listName = this.configurationInputDefinition.name;

        this.synthetizationService.suggestNamedList(rootValue, listName).subscribe({
            next: (items) => {
                this.formArray.clear();
                for (const item of items) {
                    this.formArray.push(this.createItemGroup(item));
                }
                this.notificationService.addNotification(
                    new AppNotification(`Suggested ${items.length} values for ${listName}.`, "success"),
                );
            },
            error: (error) => {
                this.errorHandlingService.addError(error, `Could not suggest values for ${listName}.`);
            },
            complete: () => {
                this.suggesting = false;
            },
        });
    }

    private createItemGroup(item?: {name?: string, description?: string}): FormGroup {
        return new FormGroup({
            name: new FormControl(item?.name ?? "", Validators.required),
            description: new FormControl(item?.description ?? ""),
        });
    }
}
