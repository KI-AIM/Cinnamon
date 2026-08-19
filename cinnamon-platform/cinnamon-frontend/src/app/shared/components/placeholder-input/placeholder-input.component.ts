import { Component, Input, TemplateRef } from '@angular/core';
import { FormControl } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";

/**
 * A placeholder that can be inserted into an input and is replaced by the backend when the content is used.
 */
interface PlaceholderDefinition {
    /**
     * Short, speaking name describing the value the placeholder is replaced with.
     */
    name: string;

    /**
     * A longer description of the value the placeholder is replaced with.
     */
    description: string;

    /**
     * An example of the value the placeholder is replaced with.
     */
    example: string;

    /**
     * The placeholder itself as it must appear in the content for the backend to replace it.
     */
    placeholder: string;
}

/**
 * A group of placeholders that belong to the same context, e.g. the invitation mail.
 */
interface PlaceholderCategory {
    /**
     * Name of the category displayed above its placeholders.
     */
    name: string;

    /**
     * The placeholders belonging to this category.
     */
    placeholders: PlaceholderDefinition[];
}

/**
 * Button opening a dialog for inserting a placeholder into the value of an input at the current cursor position.
 * Intended to be placed as the {@link https://material.angular.io/components/form-field/overview#suffix-and-prefix|matSuffix}
 * of the mat-form-field wrapping the input.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-placeholder-input',
    standalone: false,
    templateUrl: './placeholder-input.component.html',
    styleUrl: './placeholder-input.component.less'
})
export class PlaceholderInputComponent {

    /**
     * The control receiving the inserted placeholder.
     */
    @Input({required: true}) public control!: FormControl<string | null>;

    /**
     * The input the placeholder is inserted into at its current cursor position.
     */
    @Input({required: true}) public textarea!: HTMLTextAreaElement;

    /**
     * The placeholders that can be inserted, grouped into categories.
     * Kept in sync by hand with the selectors resolved by the backend's ResourceSelectorService.
     */
    protected readonly categories: PlaceholderCategory[] = [
        {
            name: 'Invitations',
            placeholders: [
                {
                    name: 'Invitation URL',
                    description: 'The URL used for accepting the invitation',
                    example: 'https://cinnamon.example.com/register/token',
                    placeholder: '${invitation.url}'
                },
                {
                    name: 'Invitation expiration date',
                    description: 'The date when the invitation expires',
                    example: '2023-01-01',
                    placeholder: '${invitation.expiresAt}'
                },
            ],
        },
    ];

    public constructor(
        private readonly matDialog: MatDialog,
    ) {
    }

    /**
     * Opens the dialog for selecting a placeholder to insert.
     * Stops the event so it does not also trigger the input the button sits in.
     *
     * @param event The click event that opened the dialog.
     * @param dialog Reference to the dialog template.
     * @protected
     */
    protected openDialog(event: Event, dialog: TemplateRef<any>): void {
        this.matDialog.open(dialog);
        event.stopPropagation();
    }

    /**
     * Inserts the given placeholder into the control at the current cursor position of the textarea, replacing the
     * selection if there is one.
     *
     * @param placeholder The placeholder to insert.
     * @protected
     */
    protected insertPlaceholder(placeholder: string): void {
        const value = this.control.value ?? '';
        const start = this.textarea.selectionStart ?? value.length;
        const end = this.textarea.selectionEnd ?? value.length;

        this.control.setValue(value.substring(0, start) + placeholder + value.substring(end));
        this.control.markAsDirty();

        // Restores focus and places the cursor right behind the inserted placeholder. Deferred so that the
        // textarea has already picked up the new value before the selection is set.
        const cursorPosition = start + placeholder.length;
        setTimeout(() => {
            this.textarea.focus();
            this.textarea.setSelectionRange(cursorPosition, cursorPosition);
        });
    }

}
