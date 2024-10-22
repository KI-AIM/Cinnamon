export class ConfigurationAdditionalConfigs {
    configs: AdditionalConfig[];
    
    constructor(configs: AdditionalConfig[]) {
        this.configs = configs; 
    }
}

export class AdditionalConfig {
    component: any; 
    title: string; 
    description: string; 

    constructor(component: any, title: string, description: string) {
        this.component = component; 
        this.title = title; 
        this.description = description; 
    }
}
