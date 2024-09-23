import { AnonymizationAttributeType } from "./anonymization-attribute-type.enum";
import { AnonymizationTransformationType } from "./anonymization-transformation-type.enum";

export class AnonymizationAttributeRowConfiguration {
    attributeName: String; 
    attributeIndex: number; 
    attributeType: AnonymizationAttributeType; 
    transformation: AnonymizationTransformationType; 
}
