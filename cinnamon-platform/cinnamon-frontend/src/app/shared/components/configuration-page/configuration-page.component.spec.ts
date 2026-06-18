import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';

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
});
