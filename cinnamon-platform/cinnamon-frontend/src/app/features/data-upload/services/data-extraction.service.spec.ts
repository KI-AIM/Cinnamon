import { HttpClient } from "@angular/common/http";
import { ProjectService } from "@shared/services/project.service";
import { of } from "rxjs";
import { DataExtractionService } from "./data-extraction.service";

describe("DataExtractionService", () => {
    it("loads the LLM profiles through the platform", () => {
        const httpClient = {
            get: jasmine.createSpy().and.returnValue(of({
                profiles: [{name: "Local Ollama", provider: "ollama", model_name: "qwen3:8b"}],
            })),
        } as unknown as HttpClient;
        const projectService = {projectIdRequiredOnce$: of("project-id")} as ProjectService;
        const service = new DataExtractionService(httpClient, projectService);

        service.getProfiles().subscribe(profiles => {
            expect(profiles[0].name).toBe("Local Ollama");
        });

        expect(httpClient.get).toHaveBeenCalledWith("/api/text-extraction/profiles");
    });

    it("extracts the configured source column through the project endpoint", () => {
        const configuration = {
            profile: "Local Ollama",
            source_column: "clinical_note",
            fields: [{name: "diagnosis", type: "string" as const, description: "Primary diagnosis"}],
        };
        const httpClient = {
            post: jasmine.createSpy().and.returnValue(of({
                status: "RUNNING",
                configuration,
                result: null,
                error: null,
            })),
        } as unknown as HttpClient;
        const projectService = {projectIdRequiredOnce$: of("project-id")} as ProjectService;
        const service = new DataExtractionService(httpClient, projectService);

        service.extract(configuration).subscribe(result => expect(result.status).toBe("RUNNING"));

        expect(httpClient.post).toHaveBeenCalledWith(
            "/api/project/project-id/text-extraction/extract",
            configuration,
        );
    });

    it("loads and applies extracted data in wide format by default", () => {
        const httpClient = {
            get: jasmine.createSpy().and.returnValue(of({status: "COMPLETED"})),
            post: jasmine.createSpy().and.returnValue(of({configurations: []})),
        } as unknown as HttpClient;
        const projectService = {projectIdRequiredOnce$: of("project-id")} as ProjectService;
        const service = new DataExtractionService(httpClient, projectService);

        service.getState().subscribe();
        service.apply().subscribe();

        expect(httpClient.get).toHaveBeenCalledWith(
            "/api/project/project-id/text-extraction",
            {params: {wide: true}},
        );
        expect(httpClient.post).toHaveBeenCalledWith(
            "/api/project/project-id/text-extraction/apply",
            {wide: true},
        );
    });

    it("can request the persisted extraction in long format", () => {
        const httpClient = {
            get: jasmine.createSpy().and.returnValue(of({status: "COMPLETED"})),
        } as unknown as HttpClient;
        const projectService = {projectIdRequiredOnce$: of("project-id")} as ProjectService;
        const service = new DataExtractionService(httpClient, projectService);

        service.getState(false).subscribe();

        expect(httpClient.get).toHaveBeenCalledWith(
            "/api/project/project-id/text-extraction",
            {params: {wide: false}},
        );
    });

    it("creates a CSV with evidence and the source text as the final column", () => {
        const service = new DataExtractionService({} as HttpClient, {} as ProjectService);
        const csv = service.createCsv({
            columns: ["row_index", "diagnosis", "source_text"],
            rows: [{row_index: 4, diagnosis: "asthma", source_text: 'Patient, "quoted"'}],
            evidence: {rows: [{
                row_index: 4,
                fields: {diagnosis: {value: "asthma", value_span: {start: 12, end: 18}}},
            }]},
        });

        expect(csv.split("\r\n")[0]).toBe('"diagnosis","evidence","source_text"');
        expect(csv).toContain('""value_span"":{""start"":12,""end"":18}');
        expect(csv.split("\r\n")[1].endsWith('"Patient, ""quoted"""')).toBeTrue();
    });

    it("parses a text extraction configuration from YAML", () => {
        const service = new DataExtractionService({} as HttpClient, {} as ProjectService);

        const configuration = service.parseConfiguration(`
text_extraction:
  profile: Local Ollama
  source_column: clinical_note
  fields:
    - name: diagnosis
      type: string
      description: Primary diagnosis
`);

        expect(configuration.fields[0].name).toBe("diagnosis");
        expect(() => service.parseConfiguration("profile: Local Ollama")).toThrowError();
    });

    it("parses objects with primitive nested fields", () => {
        const service = new DataExtractionService({} as HttpClient, {} as ProjectService);

        const configuration = service.parseConfiguration(`
text_extraction:
  profile: Local Ollama
  source_column: clinical_note
  fields:
    - name: procedures
      type: object
      description: All documented procedures
      fields:
        - name: name
          type: string
          description: Procedure name
        - name: date
          type: date
          description: Procedure date
          format: yyyy-MM-dd
`);

        expect(configuration.fields[0].type).toBe("object");
        expect(configuration.fields[0].fields?.map(field => field.name)).toEqual(["name", "date"]);
    });

    it("parses multiple object fields", () => {
        const service = new DataExtractionService({} as HttpClient, {} as ProjectService);

        const configuration = service.parseConfiguration(`
text_extraction:
  profile: Local Ollama
  source_column: clinical_note
  fields:
    - name: procedures
      type: object
      description: Procedures
      fields:
        - name: name
          type: string
          description: Procedure name
    - name: medications
      type: object
      description: Medications
      fields:
        - name: name
          type: string
          description: Medication name
`);

        expect(configuration.fields.map(field => field.name)).toEqual(["procedures", "medications"]);
    });

    it("rejects objects nested inside another object", () => {
        const service = new DataExtractionService({} as HttpClient, {} as ProjectService);

        expect(() => service.parseConfiguration(`
text_extraction:
  profile: Local Ollama
  source_column: clinical_note
  fields:
    - name: procedures
      type: object
      description: Procedures
      fields:
        - name: details
          type: object
          description: Nested details
          fields: []
`)).toThrowError();
    });
});
