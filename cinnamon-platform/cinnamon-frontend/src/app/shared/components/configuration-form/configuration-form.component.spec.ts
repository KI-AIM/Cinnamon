import { ConfigurationFormComponent } from './configuration-form.component';

describe('ConfigurationFormComponent', () => {
  let component: ConfigurationFormComponent;
  beforeEach(() => {
    component = new ConfigurationFormComponent(
      {getConfigurationName: () => 'synthetization_configuration'} as any,
      {} as any, {} as any, {} as any,
    );
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('hides the unused sample count for direct mixed-data text synthesis without changing the shared definition', () => {
    component.algorithm = {name: 'llm_mixed_data_embedding_nearest_neighbor_synthesis'} as any;
    const definition = {configurations: {sampling: {parameters: [
      {name: 'num_samples', default_value: 5}, {name: 'temperature', default_value: 0.8},
    ]}}} as any;
    const visible = component['getVisibleAlgorithmDefinition'](definition);
    expect(visible.configurations['sampling'].parameters.map(p => p.name)).toEqual(['temperature']);
    expect(definition.configurations.sampling.parameters.length).toBe(2);
    expect(component['preservesInputRows']).toBeTrue();
  });

  it('keeps the sample count for structured and text-only generators', () => {
    const definition = {configurations: {sampling: {parameters: [{name: 'num_samples'}]}}} as any;
    for (const name of ['ctgan', 'llm_text_only_embedding_nearest_neighbor_synthesis']) {
      component.algorithm = {name} as any;
      expect(component['getVisibleAlgorithmDefinition'](definition)).toBe(definition);
      expect(component['preservesInputRows']).toBeFalse();
    }
  });
});
