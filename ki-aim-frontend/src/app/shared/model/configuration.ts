export abstract class Configuration {
    getName() {
        return (<any>this).constructor.name;
        // OR return (this as any).constructor.name;
      }
}
