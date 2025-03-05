import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appAnonymizationAttributeConfigurationDirective]'
})
export class AnonymizationAttributeConfigurationDirective {

  constructor(public viewContainerRef: ViewContainerRef) { }

}
