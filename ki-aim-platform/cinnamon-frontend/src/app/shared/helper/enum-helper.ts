/**
 * If only the string value of an enum is stored, 
 * this function can be used to retrieve an object reference
 * to the enum value. Important, if an enum should be used
 * to automatically select a value in a select element
 * @param enumObj Enum reference for which the string should be searched
 * @param value String for the key of the enum
 * @returns Enum value (the number reference)
 */
export function getEnumIndexForString(enumObj: any, value: string | String): number | undefined {
    return enumObj[value as keyof typeof enumObj];
}

/**
 * Can be used to retrieve a list of strings (the enum keys)
 * by providing a list of enum values (number) 
 * @param enumObj To retrieve the string list from
 * @param values Enum instances to be used as reference
 * @returns Array<string>
 */
export function getEnumKeysByValues(enumObj: any, values: number[]): Array<string> {
    let keys: string[] = [];

    values.forEach(value => {
        // Iterate over all keys in the enum object.
        for (const key in enumObj) {
            if (enumObj[key] === value) {
                keys.push(key);
                break; 
            }
        }
    });

    return keys;
}

/**
 * Can be used to retrieve a string (the enum key)
 * by providing an enum value (number) 
 * @param enumObj To retrieve the string from
 * @param value Enum instance to be used as reference
 * @returns string | null
 */
export function getEnumKeyByValue(enumObj: any, value: number): string | null {
    // Iterate over all keys in the enum object.
    for (const key in enumObj) {
        if (enumObj[key] === value) {
            return key; 
        }
    }

    return null;
}

/**
 * Function to compare two enum instances with each other.
 * Sometimes when retrieving the enum value by deserializing
 * a JSON, it is stored as a key, so comparisons are not
 * possible. Then this functions works by performing the necessary
 * conversions before comparing them.
 * @param enumObj To be used as a source
 * @param enumMember1 Enum instance. Either key or number value
 * @param enumMember2 Enum instance. Either key or number value
 * @returns true, if enums match, false otherwise
 */
export function areEnumValuesEqual(enumObj: any, enumMember1: any, enumMember2: any): boolean {
    return processEnumValue(enumObj, enumMember1) === processEnumValue(enumObj, enumMember2); 
}

/**
 * Processes an enum instance that can be either a key
 * reference or the number value of an enum.
 * Will always transform the input to the corresponding 
 * number value of an enum. 
 * @param enumObj Enum to be used as a source.
 * @param enumMember To convert into the number value
 * @returns number value of the enum instance.
 */
export function processEnumValue(enumObj: any, enumMember: any | keyof typeof Object | number): number {
    if (typeof enumMember === "string") {
        return enumObj[enumMember as keyof typeof enumObj]; // Converts string key to corresponding enum value.
    } else if (typeof enumMember === "number" && enumMember in enumObj) {
        return enumMember; // Directly passes enum numeric values
    } else {
        console.error("Invalid enum member input.");
        return -1; // Indicates an error or invalid input
    }
}