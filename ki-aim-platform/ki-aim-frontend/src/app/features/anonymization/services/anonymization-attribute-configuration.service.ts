import { Injectable } from '@angular/core';
import { AnonymizationAttributeConfiguration } from 'src/app/shared/model/anonymization-attribute-configuration';
import { AnonymizationAttributeRowConfiguration } from 'src/app/shared/model/anonymization-attribute-row-configuration';

@Injectable({
    providedIn: 'root',
})
export class AnonymizationAttributeConfigurationService {

    private attributeConfiguration: AnonymizationAttributeConfiguration; 

    constructor() {
		this.attributeConfiguration = new AnonymizationAttributeConfiguration(); 
		this.attributeConfiguration.attributeConfiguration = new Array();
	}

	addRowConfiguration(rowConfiguration: AnonymizationAttributeRowConfiguration) {
		this.attributeConfiguration.attributeConfiguration.push(rowConfiguration); 
	}

	removeRowConfigurationById(id: number) {
		this.attributeConfiguration.attributeConfiguration = 
			this.attributeConfiguration.attributeConfiguration.filter(config => config.attributeIndex !== id); 
	}

	getAttributeConfiguration(): AnonymizationAttributeConfiguration {
		return this.attributeConfiguration; 
	}
}
