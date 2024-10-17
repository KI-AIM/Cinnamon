import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { finalize, Observable, of, share, tap } from 'rxjs';
import { AnonymizationAttributeConfiguration, AnonymizationAttributeRowConfiguration } from 'src/app/shared/model/anonymization-attribute-config';
import { environments } from 'src/environments/environment';
import { AnonymizationService } from './anonymization.service';

@Injectable({
    providedIn: 'root',
})
export class AnonymizationAttributeConfigurationService {

    private attributeConfiguration: AnonymizationAttributeConfiguration | null = null;
	private attributeConfiguration$: Observable<AnonymizationAttributeConfiguration> | null = null;

    constructor(private http: HttpClient) {
	}

	/**
	 * Creates an object representation of the data of the
	 * AnonymizationAttributeConfiguration stored in this service
	 * @returns Object<AnonymizationAttributeConfiguration>
	 */
	createConfiguration(): Object {
        return {
			...this.attributeConfiguration
         };
    }

	/**
	 * Initializes the attribute configuration with empty values
	 */
	initConfig(attributeConfigurations: AnonymizationAttributeRowConfiguration[] | null = null) {
		this.attributeConfiguration = new AnonymizationAttributeConfiguration();
		if (attributeConfigurations !== null) {
			this.attributeConfiguration.attributeConfiguration = attributeConfigurations;
		} else {
			this.attributeConfiguration.attributeConfiguration = new Array();
		}
	}

	/**
	 * Adds a new attribute row to the configuration.
	 * Initializes the configuration if it has not been
	 * initialized before
	 * @param rowConfiguration to add to the configuration.
	 */
	addRowConfiguration(rowConfiguration: AnonymizationAttributeRowConfiguration) {
		if (this.attributeConfiguration === null) {
			this.initConfig();
		}

		if (this.attributeConfiguration !== null) {
			this.attributeConfiguration.attributeConfiguration.push(rowConfiguration);
		}
	}

	/**
	 * Removes a row from the configuration with the
	 * provided index of the configuration
	 * @param id
	 */
	removeRowConfigurationById(id: number) {
		if (this.attributeConfiguration !== null) {
			this.attributeConfiguration.attributeConfiguration =
				this.attributeConfiguration.attributeConfiguration =
					this.attributeConfiguration.attributeConfiguration.filter(config => config.index !== id);
		}
	}

	/**
	 * Returns the information of the Anonymization Attribure configuration.
	 * Fetches information from backend, if it is not initialized yet
	 * @returns
	 */
	/*getAttributeConfiguration(): Observable<AnonymizationAttributeConfiguration> {
		if (this.attributeConfiguration) {
			return of(this.attributeConfiguration);
		}
		if (this.attributeConfiguration$) {
			return this.attributeConfiguration$;
		}

		this.attributeConfiguration$ = this.http.get<AnonymizationAttributeConfiguration>(environments.apiUrl + "/api/").pipe(
			tap(value => {
				this.attributeConfiguration = value;
			}),
			share(),
			finalize(() => {
				this.attributeConfiguration$ = null
			})
		);
		return this.attributeConfiguration$;
	}
	*/

	getAttributeConfiguration(): AnonymizationAttributeConfiguration | null {
		return this.attributeConfiguration;
	}

	/**
	 * Set an attribute configuration by providing
	 * the requested configuration for the anonymization.
	 * @param config object from the anonymization
	 */
	setAttributeConfiguration(config: {anonymization: {privacyModels: Object, attributeConfiguration: AnonymizationAttributeRowConfiguration[]}}) {
		console.log(config); 
		let attributeConfigs = config.anonymization.attributeConfiguration;
		this.initConfig(attributeConfigs);

		if (this.attributeConfiguration !== null) {
			this.attributeConfiguration$ = of(this.attributeConfiguration);
		}
	}
}
