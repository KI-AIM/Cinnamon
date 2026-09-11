import { of } from 'rxjs';
import { TextSynthesisConfigurationService } from '@features/synthetization/services/text-synthesis-configuration.service';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { DataConfiguration } from '@shared/model/data-configuration';
import { DataType } from '@shared/model/data-type';

import { ConfigurationPageComponent } from './configuration-page.component';

describe('ConfigurationPageComponent', () => {
  let component: ConfigurationPageComponent;
  beforeEach(() => {
    component = new ConfigurationPageComponent(
      {getConfigurationName: () => 'synthetization_configuration'} as any,
      {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any,
    );
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('treats missing free-text llm profile as submit-invalid', () => {
    const form = new FormGroup({
      text_synthesis_configuration: new FormGroup({
        synthetization_configuration: new FormGroup({
          algorithm: new FormGroup({
            synthesizer: new FormControl('llm_mixed_data_paraphrase_synthesis'),
            llm_profile: new FormGroup({
              llm_profile: new FormControl(''),
            }),
            model_parameter: new FormGroup({}),
            model_fitting: new FormGroup({}),
            sampling: new FormGroup({}),
          }),
        }),
      }),
    });
    form.get('text_synthesis_configuration.synthetization_configuration.algorithm.llm_profile.llm_profile')
      ?.setErrors({ required: true });

    (component as any).forms = { form };
    (component as any).oneEnabled = true;
    (component as any).selectedAlgorithm = { name: 'ctgan' };
    (component as any).formValid = true;

    expect(component['submitInvalid']).toBeTrue();
  });

  it('allows free-text configurations without model_fitting group', () => {
    const form = new FormGroup({
      text_synthesis_configuration: new FormGroup({
        synthetization_configuration: new FormGroup({
          algorithm: new FormGroup({
            synthesizer: new FormControl('llm_text_only_paraphrase_synthesis'),
            llm_profile: new FormGroup({
              llm_profile: new FormControl('Test Profile'),
            }),
            sampling: new FormGroup({}),
          }),
        }),
      }),
    });

    (component as any).forms = { form };
    (component as any).oneEnabled = true;
    (component as any).selectedAlgorithm = { name: 'ctgan' };
    (component as any).formValid = true;

    expect(component['submitInvalid']).toBeFalse();
  });

  it('uses free-text headers and four steps for text-only datasets', () => {
    const dataConfiguration = new DataConfiguration();
    dataConfiguration.configurations = [{ type: DataType.TEXT } as any];

    expect(component['getSelectionStepHeader'](dataConfiguration)).toBe('Select the free-text synthesizer');
    expect(component['getConfigurationStepHeader'](dataConfiguration)).toBe('Configure the free-text synthesizer');
    expect(component['getNumberSteps'](dataConfiguration)).toBe(4);
    expect(component['shouldShowFreeTextSteps'](dataConfiguration)).toBeFalse();
  });

  it('uses structured synthesis and separate text steps for mixed data', () => {
    const dataConfiguration = new DataConfiguration();
    dataConfiguration.configurations = [{ type: DataType.INTEGER } as any, { type: DataType.TEXT } as any];

    expect(component['getSelectionStepHeader'](dataConfiguration)).toBe('Select the structured synthesizer');
    expect(component['getConfigurationStepHeader'](dataConfiguration)).toBe('Configure the structured synthesizer');
    expect(component['getNumberSteps'](dataConfiguration)).toBe(6);
    expect(component['shouldShowFreeTextSteps'](dataConfiguration)).toBeTrue();
  });

  it('auto-resolves the only available text-only synthesizer', () => {
    const dataConfiguration = new DataConfiguration();
    dataConfiguration.configurations = [{ type: DataType.TEXT } as any];

    const algorithm = {
      name: 'llm_text_only_paraphrase_synthesis',
      processing_capabilities: {
        data_modality: 'text_only',
      },
    } as any;

    expect(component['getEffectiveSelectedAlgorithm']([algorithm], dataConfiguration)).toEqual(algorithm);
  });

  it('offers every structured generator and tuning for mixed data', () => {
    const data = new DataConfiguration();
    data.configurations = [{type: DataType.INTEGER}, {type: DataType.TEXT}] as any;
    const structured = ['ctgan', 'tvae', 'arf', 'ddpm', 'rtvae', 'bayesian_network', 'llm_tabular']
      .map(name => ({name, processing_capabilities: {data_modality: 'structured_only'}} as any));
    const mixed = {name: 'llm_mixed_data_paraphrase_synthesis'} as any;
    const text = {name: 'llm_text_only_paraphrase_synthesis'} as any;
    expect(component['getPrimaryAlgorithms']([...structured, mixed, text], data)).toEqual(structured);
    expect(component['getFreeTextAlgorithms']([...structured, mixed, text])).toEqual([mixed]);
    component.intermediateStep = {} as any;
    expect(component['shouldShowIntermediateStep'](data)).toBeTrue();
    expect(component['getTotalStepCount'](data)).toBe(7);
    expect(component['getFreeTextConfigurationStepIndex'](data)).toBe(6);
  });

  it('loads a text definition once and keeps edited controls across change detection', () => {
    const textService = new TextSynthesisConfigurationService(new FormBuilder());
    const sync = spyOn(textService, 'syncFormWithDefinition').and.callThrough();
    const definition = {parameters: [], configurations: {
      sampling: {parameters: [{name: 'temperature', default_value: 0.2, type: 'float', min_value: 0, max_value: 2}]},
    }} as any;
    const algorithm = {name: 'llm_mixed_data_paraphrase_synthesis'} as any;
    const algorithmService = {getConfigurationName: () => 'synthetization_configuration',
                              getAlgorithmDefinition: () => of(definition)} as any;
    component = new ConfigurationPageComponent(algorithmService,
      {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any, textService);
    const root: FormGroup = new FormGroup({});
    textService.initForm(root, {synthetization_configuration: {algorithm: {synthesizer: algorithm.name}}} as any, false);
    (component as any).forms = {form: root};
    const data = new DataConfiguration();
    const first = component['getFreeTextAlgorithmDefinition']([algorithm], data);
    first.subscribe();
    const temperature = root.get('text_synthesis_configuration.synthetization_configuration.algorithm.sampling.temperature') as FormControl;
    temperature.setValue(0.7);
    const second = component['getFreeTextAlgorithmDefinition']([algorithm], data);
    second.subscribe();
    expect(first).toBe(second);
    expect(sync).toHaveBeenCalledTimes(1);
    expect(temperature.value).toBe(0.7);
  });

});
