import { FhirFileConfiguration, FileConfiguration } from "../../../shared/model/file-configuration";
import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from "../../../shared/model/csv-file-configuration";
import { XlsxFileConfiguration } from "src/app/shared/model/xlsx-file-configuration";
import { HttpClient } from "@angular/common/http";
import {environments} from "../../../../environments/environment";
import { finalize, Observable, of, share, tap } from "rxjs";
import {Injectable} from "@angular/core";
import {FileInformation} from "../../../shared/model/file-information";

@Injectable({
    providedIn: 'root',
})
export class FileService {
    private readonly baseUrl: string = environments.apiUrl + "/api/data/file";

    fileConfiguration: FileConfiguration;

    private _fileInfo: FileInformation | null = null;
    private _fileInfo$: Observable<FileInformation> | null = null;

	constructor(
        private readonly httpClient: HttpClient,
    ) {
        this.fileConfiguration = new FileConfiguration(
            null,
            new CsvFileConfiguration(Delimiter.COMMA, LineEnding.LF, QuoteChar.DOUBLE_QUOTE, true),
            new XlsxFileConfiguration(true),
            new FhirFileConfiguration(""));
    }

    public get fileInfo$(): Observable<FileInformation> {
        if (this._fileInfo) {
            return of(this._fileInfo);
        }
        if (this._fileInfo$) {
            return this._fileInfo$;
        }

        return this.httpClient.get<FileInformation>(this.baseUrl).pipe(
            tap(value => this._fileInfo = value),
            share(),
            finalize(() => {
                this._fileInfo$ = null;
            })
        );
    }

    public invalidateCache() {
        this._fileInfo = null;
        this._fileInfo$ = null;
    }

    public getFileConfiguration(): FileConfiguration {
        return this.fileConfiguration;
    }

	public setFileConfiguration(value: FileConfiguration) {
		this.fileConfiguration = value;
	}

    public uploadFile(file: File, fileConfiguration: FileConfiguration): Observable<FileInformation> {
        const formData = new FormData();

        formData.append("file", file);
        const fileConfigString = JSON.stringify(fileConfiguration);
        formData.append("fileConfiguration", fileConfigString);

        return this.httpClient.post<FileInformation>(this.baseUrl, formData).pipe(tap(value => {
            this._fileInfo = value;
        }));
    }
}
