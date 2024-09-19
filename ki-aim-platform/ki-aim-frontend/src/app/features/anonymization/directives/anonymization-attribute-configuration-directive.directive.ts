import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appAnonymizationAttributeConfigurationDirective]'
})
export class AnonymizationAttributeConfigurationDirectiveDirective {

  constructor(public viewContainerRef: ViewContainerRef) { }

}
