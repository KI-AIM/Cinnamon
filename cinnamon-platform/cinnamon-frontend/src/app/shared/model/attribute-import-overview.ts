import { Type } from "class-transformer";
import { ColumnConfiguration } from "./column-configuration";

/**
 * Attribute that is only present in the data and is missing a configuration.
 */
export class MissingAttribute {
    index: number;
    /**
     * Name of the attribute in the data. Empty if the data has no header.
     */
    name: string;
    @Type(() => ColumnConfiguration)
    suggestedConfiguration: ColumnConfiguration | null;
    confidence: number | null;
}

export class ImportConfiguration {
    attributeMatching: "NAME" | "INDEX";
    handleConfigOnly: "ERROR" | "IGNORE";
    handleDataOnly: "ERROR" | "ESTIMATE_CONFIG" | "REMOVE_ATTRIBUTE";
    /**
     * Configurations of the attributes matched to the data.
     */
    @Type(() => ColumnConfiguration)
    attributes: ColumnConfiguration[] = [];
}

/**
 * Overview of the matching between the attribute configurations and the attributes in the data.
 */
export class AttributeImportOverview {
    @Type(() => ImportConfiguration)
    importConfiguration: ImportConfiguration;
    /**
     * Attributes only present in the configuration, they are ignored.
     */
    @Type(() => ColumnConfiguration)
    configOnlyAttributes: ColumnConfiguration[] = [];
    /**
     * Attributes only present in the data, they are missing a configuration.
     */
    @Type(() => MissingAttribute)
    dataOnlyAttributes: MissingAttribute[] = [];
}
