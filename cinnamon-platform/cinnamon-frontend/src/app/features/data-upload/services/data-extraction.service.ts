import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ProjectService } from "@shared/services/project.service";
import { DataConfiguration } from "@shared/model/data-configuration";
import { map, Observable, switchMap } from "rxjs";
import { environments } from "src/environments/environment";
import { parse } from "yaml";

export interface LlmProfile {
    name: string;
    provider: string;
    model_name: string;
}

export type PrimitiveExtractionFieldType = "boolean" | "date" | "date_time" | "decimal" | "integer" | "string";
export type ExtractionFieldType = PrimitiveExtractionFieldType | "object";

export interface ExtractionField {
    name: string;
    type: ExtractionFieldType;
    description: string;
    allowed_values?: string[];
    min_value?: number | string;
    max_value?: number | string;
    format?: string;
    fields?: ExtractionField[];
}

export interface DataExtractionConfiguration {
    profile: string;
    source_column: string;
    fields: ExtractionField[];
}

export interface ExtractionTable {
    columns: string[];
    rows: Array<Record<string, unknown>>;
    evidence?: {
        rows: Array<{
            row_index: number;
            source_row_index?: number;
            fields: Record<string, unknown>;
        }>;
    };
}

export interface TextExtractionState {
    status: "NOT_STARTED" | "RUNNING" | "COMPLETED" | "FAILED";
    configuration: DataExtractionConfiguration | null;
    result: ExtractionTable | null;
    error: string | null;
}

@Injectable({providedIn: "root"})
export class DataExtractionService {
    public readonly CONFIGURATION_NAME = "text_extraction";
    public configuration: DataExtractionConfiguration | null = null;
    public result: ExtractionTable | null = null;
    public wideFormat = true;
    public extractionEnabled = true;

    constructor(
        private readonly httpClient: HttpClient,
        private readonly projectService: ProjectService,
    ) {
    }

    public getProfiles(): Observable<LlmProfile[]> {
        return this.httpClient.get<{profiles: LlmProfile[]}>(
            `${environments.apiUrl}/api/text-extraction/profiles`,
        ).pipe(map(response => response.profiles ?? []));
    }

    public extract(configuration: DataExtractionConfiguration): Observable<TextExtractionState> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.httpClient.post<TextExtractionState>(
                `${environments.apiUrl}/api/project/${projectId}/text-extraction/extract`,
                configuration,
            )),
        );
    }

    public getState(wide = true): Observable<TextExtractionState> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.httpClient.get<TextExtractionState>(
                `${environments.apiUrl}/api/project/${projectId}/text-extraction`,
                {params: {wide}},
            )),
        );
    }

    public apply(wide = true): Observable<DataConfiguration> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.httpClient.post<DataConfiguration>(
                `${environments.apiUrl}/api/project/${projectId}/text-extraction/apply`,
                {wide},
            )),
        );
    }

    public createCsv(table: ExtractionTable): string {
        const columns = [
            ...table.columns.filter(column => !["row_index", "source_text"].includes(column)),
            "evidence",
            "source_text",
        ];
        const evidence = new Map((table.evidence?.rows ?? []).map(row => [row.row_index, row.fields]));
        const cell = (value: unknown): string => {
            const text = value == null ? "" : typeof value === "object" ? JSON.stringify(value) : String(value);
            return `"${text.replaceAll('"', '""')}"`;
        };
        return [
            columns.map(cell).join(","),
            ...table.rows.map(row => columns.map(column => cell(
                column === "evidence" ? evidence.get(Number(row["row_index"])) ?? {} : row[column],
            )).join(",")),
        ].join("\r\n");
    }

    public parseConfiguration(contents: string): DataExtractionConfiguration {
        const document = parse(contents);
        const configuration = document?.[this.CONFIGURATION_NAME];
        const primitiveTypes: PrimitiveExtractionFieldType[] = [
            "boolean", "date", "date_time", "decimal", "integer", "string",
        ];
        const structuredTypes: ExtractionFieldType[] = ["object"];
        const isField = (field: any, nested = false): boolean => {
            if (typeof field?.name !== "string"
                || typeof field?.description !== "string"
                || ![...primitiveTypes, ...structuredTypes].includes(field?.type)) {
                return false;
            }
            if (!structuredTypes.includes(field.type)) {
                return true;
            }
            return !nested
                && Array.isArray(field.fields)
                && field.fields.length > 0
                && field.fields.every((nestedField: any) => primitiveTypes.includes(nestedField?.type)
                    && isField(nestedField, true));
        };
        if (typeof configuration?.profile !== "string"
            || typeof configuration?.source_column !== "string"
            || !Array.isArray(configuration?.fields)
            || configuration.fields.length === 0
            || configuration.fields.some((field: any) => !isField(field))) {
            throw new Error(`Invalid '${this.CONFIGURATION_NAME}' configuration.`);
        }
        return configuration as DataExtractionConfiguration;
    }
}
