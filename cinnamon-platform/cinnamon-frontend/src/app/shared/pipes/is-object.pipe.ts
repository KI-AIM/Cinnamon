import { Pipe, PipeTransform } from '@angular/core';
import { ConfigurationObject, ConfigurationObjectType } from "@shared/services/algorithm.service";

/**
 * Checks if the given value in a configuration object is a nested group or a parameter with a primitive value.
 *
 * @author Daniel Preciado-Marquez
 */
@Pipe({
  name: 'isObject',
  standalone: false
})
export class IsObjectPipe implements PipeTransform {

    public transform(value: ConfigurationObjectType): ConfigurationObject | null {
        return typeof value === 'object' ? value as ConfigurationObject : null;
    }

}
