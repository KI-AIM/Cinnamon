export enum LineEnding {
    CR = "\r",
    CRLF = "\r\n",
    LF = "\n",
}

export enum Delimiter {
    COMMA = ",",
    SEMICOLON = ";",
}

export enum QuoteChar {
    DOUBLE_QUOTE = "\"",
    SINGLE_QUOTE = "'",
}

export class CsvFileConfiguration {
    constructor(
        public columnSeparator: Delimiter,
        public lineSeparator: LineEnding,
        public quoteChar: QuoteChar,
        public hasHeader: boolean,
    ) {}

}
