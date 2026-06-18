import { ViewContainerRef } from '@angular/core';
import { AnonymizationAttributeConfigurationDirective } from './anonymization-attribute-configuration.directive';

describe('Directive: AnonymizationAttributeConfigurationDirective', () => {
  it('should create an instance', () => {
    const directive = new AnonymizationAttributeConfigurationDirective({} as ViewContainerRef);
    expect(directive).toBeTruthy();
  });
});
