import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { DataConfiguration } from '@shared/model/data-configuration';
import { DataType } from '@shared/model/data-type';

import { ConfigurationPageComponent } from './configuration-page.component';

describe('ConfigurationPageComponent', () => {
  let component: ConfigurationPageComponent;
  let fixture: ComponentFixture<ConfigurationPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationPageComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('treats missing free-text llm profile as submit-invalid', () => {
    const form = new FormGroup({
      text_synthesis_configuration: new FormGroup({
        synthetization_configuration: new FormGroup({
          algorithm: new FormGroup({
            synthesizer: new FormControl('llm_nearest_neighbor_few_shot_text_synthesis'),
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

  it('uses free-text headers and four steps for text-only datasets', () => {
    const dataConfiguration = new DataConfiguration();
    dataConfiguration.configurations = [{ type: DataType.TEXT } as any];

    expect(component['getSelectionStepHeader'](dataConfiguration)).toBe('Select the free-text synthesizer');
    expect(component['getConfigurationStepHeader'](dataConfiguration)).toBe('Configure the free-text synthesizer');
    expect(component['getNumberSteps'](dataConfiguration)).toBe(4);
    expect(component['shouldShowFreeTextSteps'](dataConfiguration)).toBeFalse();
  });

  it('keeps the free-text pipeline only for mixed datasets', () => {
    const dataConfiguration = new DataConfiguration();
    dataConfiguration.configurations = [{ type: DataType.INTEGER } as any, { type: DataType.TEXT } as any];

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
        generation_scope: 'text_only',
      },
    } as any;

    expect(component['getEffectiveSelectedAlgorithm']([algorithm], dataConfiguration)).toEqual(algorithm);
  });

});
