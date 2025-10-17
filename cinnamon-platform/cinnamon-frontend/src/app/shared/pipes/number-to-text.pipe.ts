import { Pipe, PipeTransform } from '@angular/core';

/**
 * Converts a number into a textual representation.
 * Writes integers between 0 and 10 into a textual representation.
 * Other Values are kept in a numerical form.
 */
@Pipe({
    name: 'numberToText',
    standalone: false,
})
export class NumberToTextPipe implements PipeTransform {

    public transform(value: number): string {
        switch (value) {
            case 0: return "zero";
            case 1: return "one";
            case 2: return "two";
            case 3: return "three";
            case 4: return "four";
            case 5: return "five";
            case 6: return "six";
            case 7: return "seven";
            case 8: return "eight";
            case 9: return "nine";
            default: return value + "";
        }
    }

}
