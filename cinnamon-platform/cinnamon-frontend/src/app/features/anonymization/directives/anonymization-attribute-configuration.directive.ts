import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
    selector: '[appAnonymizationAttributeConfigurationDirective]',
    standalone: false
})
export class AnonymizationAttributeConfigurationDirective {

  constructor(public viewContainerRef: ViewContainerRef) { }

}
