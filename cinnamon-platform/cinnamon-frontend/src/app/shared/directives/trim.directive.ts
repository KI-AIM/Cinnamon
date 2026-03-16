import { Directive, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from "@angular/forms";

/**
 * Directive to trim the value of an input field.
 *
 * @author Daniel Preciado-Marquez
 */
@Directive({
    selector: '[appTrim]',
    standalone: false
})
export class TrimDirective {

    constructor(
        @Self() @Optional() private readonly ngControl: NgControl,
    ) {
    }

    @HostListener('blur')
    public onBlur() {
        const control = this.ngControl?.control;
        const originalValue = control?.value;

        if (typeof originalValue !== 'string') {
            return;
        }

        const trimmedValue = originalValue.trim();

        if (originalValue === trimmedValue) {
            return;
        }

        control?.patchValue(trimmedValue);
    }
}
