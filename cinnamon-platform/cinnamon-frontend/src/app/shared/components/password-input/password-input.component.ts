import { Component, Input, TemplateRef } from '@angular/core';
import { FormControl } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { PasswordRequirements } from "@shared/services/app-config.service";

@Component({
    selector: 'app-password-input',
    standalone: false,
    templateUrl: './password-input.component.html',
    styleUrl: './password-input.component.less'
})
export class PasswordInputComponent {

    @Input({required: true}) public control!: FormControl<string>;
    @Input({required: true}) public label!: string;

    @Input() public autocomplete: 'current-password' | 'new-password' = 'current-password';
    @Input() public heading: string | null = null;
    @Input() public passwordRequirements: PasswordRequirements | null = null;

    protected hidePassword: boolean = true;

    public constructor(
        private readonly matDialog: MatDialog,
    ) {
    }

    /**
     * Opens the dialog contained in the given template.
     * @param ref Reference to the template element.
     * @protected
     */
    protected openDialog(ref: TemplateRef<any>) {
        this.matDialog.open(ref);
    }

    /**
     * Creates the error message for the password field.
     * @protected
     */
    protected createPasswordErrorMessage(): string | null {
        if (this.control.errors == null) {
            return null;
        }

        const errors: string[] = [];

        if (this.control.hasError('required')) {
            errors.push('not be empty');
        }
        if (this.control.hasError('length')) {
            errors.push(`be at least ${this.control.getError('length').minLength} characters long`);
        }
        if (this.control.hasError('digit')) {
            errors.push('contain at lest one digit')
        }
        if (this.control.hasError('lowercase')) {
            errors.push(`contain at least one lowercase character`);
        }
        if (this.control.hasError('uppercase')) {
            errors.push(`contain at least one uppercase character`);
        }
        if (this.control.hasError('specialChar')) {
            errors.push(`contain at least one special character`);
        }
        if (this.control.hasError('passwordMatch')) {
            errors.push('match');
        }

        return "Password must " + errors.join(", ");
    }

}
