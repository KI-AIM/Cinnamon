export class HistogramEntry {
    columnData: Map<string, number>;   

    constructor(data?: { columnData: { [key: string]: number } }) {
        if (data && data.columnData) {
            const entries = Object.entries(data.columnData).map(([key, value]) => [key, value] as [string, number]);
            this.columnData = new Map<string, number>(entries);
        } else {
            this.columnData = new Map<string, number>();
        }
    }

    getLargestNumber(): number {
        var result = 0; 

        this.columnData.forEach((value, _) => {
            if (value > result) {
                result = value; 
            }
        }); 

        return result; 
    }
}

export class HistogramData {
    data: Map<string, HistogramEntry>; 

    constructor(jsonData?: { data: { [key: string]: { columnData: { [key: string]: number } } } }) {
        this.data = new Map<string, HistogramEntry>();
        if (jsonData && jsonData.data) {

            Object.entries(jsonData.data).forEach(([key, value]) => {
                this.data.set(key, new HistogramEntry({ columnData: value.columnData }));
            }); 

        }
    }

    public getEntry(key: string): HistogramEntry | undefined {
        return this.data.get(key);
    }

    public addEntry(key: string, entry: HistogramEntry): void {
        this.data.set(key, entry);
    }

    public hasEntry(key: string): boolean {
        return this.data.has(key);
    }

    // Allow iteration over keys
    public getKeys(): IterableIterator<string> {
        return this.data.keys();
    }
}
