import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'instanceOf'
})
export class InstanceOfPipe implements PipeTransform {

    transform<T>(value: any, targetType: new (...args: any[]) => T): T | null {
        return value instanceof targetType ? (value as T) : null;
    }

}
