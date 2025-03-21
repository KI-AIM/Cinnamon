import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator, ValidatorFn } from '@angular/forms';

export function noSpaceValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      return (typeof control.value === 'string') && control.value.trim().includes(" ")
        ? { noSpace: { value: control.value } }
        : null
    }
}

@Directive({
    selector: '[appNoSpaceValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: NoSpaceValidatorDirective,
            multi: true,
        }
    ],
    standalone: false
})
export class NoSpaceValidatorDirective implements Validator {

  constructor() { }

  validate(control: AbstractControl<any, any>): ValidationErrors | null {
    return noSpaceValidator()(control);
  }
}
