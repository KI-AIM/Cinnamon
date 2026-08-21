import { booleanAttribute, Component, Input, TemplateRef } from '@angular/core';
import { FormControl } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { AdminService } from "@shared/services/admin.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";

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
 * A group of placeholders that belong to the same context.
 */
interface PlaceholderCategory {
    /**
     * The name of the category displayed above its placeholders.
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
     * Whether the input is disabled.
     */
    @Input({transform: booleanAttribute}) public textareaDisabled: boolean = false;

    /**
     * The input used for previewing the placeholder content.
     */
    @Input() public previewInput: HTMLTextAreaElement | null = null;

    /**
     * The ID of the invitation for which the placeholders are inserted. Used for previewing the placeholder content.
     */
    @Input() public contextInvitationId: string | null = null;

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

    protected readonly formatting: PlaceholderCategory[] = [
        {
            name: 'Timestamp Formatting',
            placeholders: [
                {
                    name: 'Absolute',
                    description: 'Displays the date and time in a fixed format.',
                    example: '2023-01-01 12:00:00',
                    placeholder: 'absolute'
                },
                {
                    name: 'Relative',
                    description: 'Displays the date and time relative to the current moment.',
                    example: 'in 1 day',
                    placeholder: 'relative'
                },
                {
                    name: 'Absolute + Relative',
                    description: 'Displays the date and time in both absolute and relative formats.',
                    example: 'in 1 day (2023-01-01 12:00:00)',
                    placeholder: 'combined'
                },
                {
                    name: 'Smart',
                    description: 'Uses relative formatting for recent timestamps and absolute formatting for older timestamps',
                    example: '',
                    placeholder: 'smart'
                },
            ],
        },
    ];

    /**
     * Whether the preview is currently enabled, and the preview input is shown instead of the textarea.
     * @protected
     */
    protected isPreviewEnabled = false;

    public constructor(
        private readonly matDialog: MatDialog,
        private readonly adminService: AdminService,
        private readonly errorHandlingService: ErrorHandlingService,
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

    protected insertFormatting(formatting: string): void {
        const value = this.control.value ?? '';
        const start = this.textarea.selectionStart ?? value.length;
        const end = this.textarea.selectionEnd ?? value.length;

        // Check if the current cursor position is inside a placeholder
        const placeholderStart = value.lastIndexOf('${', start);
        const placeholderEnd = value.indexOf('}', start);

        if (placeholderStart === -1 || placeholderEnd === -1 || placeholderStart >= start || placeholderEnd <= end) {
            return;
        }

        const placeholderContent = value.substring(placeholderStart + 2, placeholderEnd);
        console.log(placeholderContent);

        // Find the end position for inserting the formatting, take default values in consideration
        const defaultValueIndex = placeholderContent.indexOf(':');
        const formattingEnd = defaultValueIndex !== -1 ? defaultValueIndex : placeholderContent.length;

        // Find the start position for inserting the formatting, take existing formatting into consideration
        const existingFormattingIndex = placeholderContent.indexOf('|');
        const formattingStart = existingFormattingIndex !== -1 ? existingFormattingIndex : formattingEnd;


        // Insert the formatting inside the placeholder
        const newValue = value.substring(0, placeholderStart + 2 + formattingStart) + "|" + formatting + value.substring(placeholderStart + 2 + formattingEnd);
        this.control.setValue(newValue);
        this.control.markAsDirty();

        // Restore focus and place the cursor right after the inserted formatting
        const cursorPosition = placeholderStart + 2 + formattingStart + formatting.length;
        setTimeout(() => {
            this.textarea.focus();
            this.textarea.setSelectionRange(cursorPosition, cursorPosition);
        });
    }

    /**
     * Toggles the preview of the placeholder content.
     * If the preview is enabled, the preview content is updated by sending the current value of the control to the backend for processing.
     * @protected
     */
    protected togglePreview(): void {
        if (this.previewInput == null) {
            return;
        }

        if (this.isPreviewEnabled) {
            this.isPreviewEnabled = false;
            this.previewInput.hidden = true;
            this.textarea.hidden = false;

            if (!this.textareaDisabled) {
                this.control.enable()
            }
        } else {

            const body = this.control.value;
            if (body == null || body.trim() === '') {
                return;
            }

            this.adminService.previewText(body, this.contextInvitationId).subscribe({
                next: preview => {
                    this.isPreviewEnabled = true;
                    this.control.disable();
                    this.previewInput!.value = preview;
                    this.previewInput!.hidden = false;
                    this.textarea.hidden = true;
                },
                error: e => {
                    this.errorHandlingService.addError(e);
                },
            });
        }
    }

}
