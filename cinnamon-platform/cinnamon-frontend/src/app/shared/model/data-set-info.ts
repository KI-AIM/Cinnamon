import { Type } from "class-transformer";

export class DataSetInfo {
    numberRows: number;
    numberInvalidRows: number;
    hasHoldOutSplit: boolean;
    holdOutPercentage: number;
    numberHoldOutRows: number;
    numberInvalidHoldOutRows: number;
    numberRetainedRows: number | null;

    @Type(() => DataConfigurationInfo)
    dataConfigurationInfo: DataConfigurationInfo;

    public get numberNotHoldOutRows(): number {
        return this.numberRows - this.numberHoldOutRows;
    }

    public get numberInvalidNotHoldOutRows(): number {
        return this.numberInvalidRows - this.numberInvalidHoldOutRows;
    }
}

export class DataConfigurationInfo {
    numberColumns: number;
    numberNumericColumns: number;
    numberCategoricalColumns: number;
    numberDateColumns: number;
}
