import { DataType } from "@shared/model/data-type";

export enum DataScale {
    DATE = "DATE",
    NOMINAL = "NOMINAL",
    ORDINAL = "ORDINAL",
    INTERVAL = "INTERVAL",
    RATIO = "RATIO",
}

/**
 * Metadata for data scales.
 */
interface DataScaleAttributes {
    /**
     * List of data types for which the scale can be selected.
     */
    applicableTo: DataType[];
    /**
     * Name for displaying the scale in the UI.
     */
    displayName: string;
}

/**
 * Additional metadata for each data type.
 */
export const DataScaleMetadata: Record<DataScale, DataScaleAttributes> = {
    [DataScale.NOMINAL]: {
        applicableTo: [DataType.BOOLEAN, DataType.DECIMAL, DataType.INTEGER, DataType.STRING],
        displayName: 'Nominal',
    },
    [DataScale.ORDINAL]: {
        applicableTo: [DataType.DECIMAL, DataType.INTEGER, DataType.STRING],
        displayName: 'Ordinal',
    },
    [DataScale.INTERVAL]: {
        applicableTo: [DataType.DECIMAL, DataType.INTEGER],
        displayName: 'Interval',
    },
    [DataScale.RATIO]: {
        applicableTo: [DataType.DECIMAL],
        displayName: 'Ratio',
    },
    [DataScale.DATE]: {
        applicableTo: [DataType.DATE, DataType.DATE_TIME],
        displayName: 'Date',
    },
}
