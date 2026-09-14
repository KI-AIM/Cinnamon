import { FormArray, FormBuilder } from "@angular/forms";
import { MissingAttribute } from "@shared/model/attribute-import-overview";
import { ColumnConfiguration } from "@shared/model/column-configuration";
import { DataConfiguration } from "@shared/model/data-configuration";
import { DataScale } from "@shared/model/data-scale";
import { DataType } from "@shared/model/data-type";
import { plainToInstance } from "class-transformer";
import { of } from "rxjs";

import { DataConfigurationComponent } from "./data-configuration.component";

describe("DataConfigurationComponent", () => {
	let component: any;
	let configurationService: any;
	let dataService: any;

	function column(index: number, name: string): ColumnConfiguration {
		return plainToInstance(ColumnConfiguration, {
			index: index, name: name, type: DataType.STRING, scale: DataScale.NOMINAL, configurations: [],
		});
	}

	function missing(index: number, name: string, suggestion: ColumnConfiguration | null = null): MissingAttribute {
		return plainToInstance(MissingAttribute, {
			index: index, name: name, suggestedConfiguration: suggestion, confidence: suggestion ? 0.8 : null,
		});
	}

	function formIndices(): number[] {
		return (component.attributeConfigurationForm.controls['configurations'] as FormArray).controls
			.map(c => c.get('index')!.value);
	}

	function formNames(): string[] {
		return (component.attributeConfigurationForm.controls['configurations'] as FormArray).controls
			.map(c => c.get('name')!.value);
	}

	beforeEach(() => {
		configurationService = {
			confidence: null,
			localConfigOnlyAttributes: null,
			localDataOnlyAttributes: null,
			storeAttributeMatching: jasmine.createSpy('storeAttributeMatching').and.returnValue(of({})),
		};
		dataService = {
			storeData: jasmine.createSpy('storeData').and.returnValue(of(1)),
		};

		component = new DataConfigurationComponent(
			configurationService,
			dataService,
			{invalidateCache: jasmine.createSpy('invalidateCache')} as any,
			{addError: jasmine.createSpy('addError')} as any,
			{} as any,
			new FormBuilder(),
			{
				isStepCompleted: () => false,
				updateNextStep: jasmine.createSpy('updateNextStep').and.returnValue(of(null)),
			} as any,
			{setLoadingStatus: jasmine.createSpy('setLoadingStatus')} as any,
			{setAndRouteToStep: () => of(null)} as any,
		);

		const dataConfiguration = new DataConfiguration();
		dataConfiguration.configurations = [column(0, "a"), column(2, "c")];
		component.attributeConfigurationForm = component.createAttributeConfigurationForm(dataConfiguration, null);
		component.createDataSetConfigurationForm(false, 0);
	});

	it("should add a missing attribute with the estimated configuration in index order", () => {
		const suggestion = column(1, "");
		suggestion.type = DataType.INTEGER;
		suggestion.scale = DataScale.INTERVAL;
		const attribute = missing(1, "b", suggestion);
		component.dataOnlyAttributes = [attribute];

		component.addDataOnlyAttribute(attribute);

		expect(formIndices()).toEqual([0, 1, 2]);
		expect(formNames()).toEqual(["a", "b", "c"]);
		const added = (component.attributeConfigurationForm.controls['configurations'] as FormArray).at(1);
		expect(added.get('type')!.value).toEqual(DataType.INTEGER);
		expect(component.dataOnlyAttributes).toEqual([]);
		expect(component.getConfidence(1)).toEqual(0.8);
	});

	it("should name a missing attribute without header by its index", () => {
		const attribute = missing(3, "");
		component.dataOnlyAttributes = [attribute];

		component.addDataOnlyAttribute(attribute);

		expect(formNames()).toEqual(["a", "c", "column_3"]);
		// No estimation available, the type has to be selected by the user
		expect(component.attributeConfigurationForm.invalid).toBeTrue();
	});

	it("should assign an ignored configuration to a missing attribute", () => {
		const configOnly = column(7, "old_name");
		const dataOnly = missing(1, "b");
		component.configOnlyAttributes = [configOnly];
		component.dataOnlyAttributes = [dataOnly];

		component.assignConfigOnlyAttribute(configOnly, dataOnly);

		expect(formIndices()).toEqual([0, 1, 2]);
		expect(formNames()).toEqual(["a", "b", "c"]);
		expect(component.configOnlyAttributes).toEqual([]);
		expect(component.dataOnlyAttributes).toEqual([]);
		expect(configurationService.localConfigOnlyAttributes).toEqual([]);
	});

	it("should discard an ignored configuration", () => {
		const configOnly = column(7, "old_name");
		component.configOnlyAttributes = [configOnly];

		component.removeConfigOnlyAttribute(configOnly);

		expect(component.configOnlyAttributes).toEqual([]);
		expect(formIndices()).toEqual([0, 2]);
	});

	it("should be invalid while attributes are missing", () => {
		expect(component.isInvalid).toBeFalse();

		component.dataOnlyAttributes = [missing(1, "b")];

		expect(component.isInvalid).toBeTrue();
	});

	it("should store the matching before storing the data", () => {
		const configOnly = column(7, "old_name");
		component.configOnlyAttributes = [configOnly];

		component.confirmConfiguration();

		expect(configurationService.storeAttributeMatching).toHaveBeenCalledTimes(1);
		const [attributes, unmatched] = configurationService.storeAttributeMatching.calls.mostRecent().args;
		expect(attributes.map((a: ColumnConfiguration) => a.name)).toEqual(["a", "c"]);
		expect(unmatched).toEqual([configOnly]);
		expect(dataService.storeData).toHaveBeenCalledTimes(1);
	});
});
