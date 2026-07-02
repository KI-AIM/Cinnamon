import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from "@shared/model/csv-file-configuration";
import {
    DataSourceConfiguration,
    DataSourceType,
    FhirFileConfiguration,
    FileConfiguration,
    FileConfigurationEstimation
} from "@shared/model/file-configuration";
import { FileInformation } from "@shared/model/file-information";
import { XlsxFileConfiguration } from "@shared/model/xlsx-file-configuration";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectService } from "@shared/services/project.service";
import { distinctUntilChanged, finalize, Observable, ReplaySubject, share, shareReplay, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root',
})
export class FileService {
    fileConfiguration: FileConfiguration;

    private readonly _fileInfoSubject: ReplaySubject<FileInformation>;
    private readonly _fileInfo$: Observable<FileInformation>;
    private _fileInfoFetched: boolean = false;
    private _fileInfoLoading$: Observable<FileInformation> | null = null;

    private readonly _dataSourceConfigurationSubject: ReplaySubject<DataSourceConfiguration>;
    private readonly _dataSourceConfiguration$: Observable<DataSourceConfiguration>;
    private _dataSourceConfigurationFetched: boolean = false;
    private _dataSourceConfigurationLoading$: Observable<DataSourceConfiguration> | null = null;

	constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly httpClient: HttpClient,
        private readonly projectService: ProjectService,
    ) {
        this._fileInfoSubject = new ReplaySubject<FileInformation>(1);
        this._fileInfo$ = this._fileInfoSubject.asObservable().pipe(
            distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
        );

        this._dataSourceConfigurationSubject = new ReplaySubject<DataSourceConfiguration>(1);
        this._dataSourceConfiguration$ = this._dataSourceConfigurationSubject.asObservable().pipe(
            distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
        );

        this.fileConfiguration = new FileConfiguration(
            null,
            new CsvFileConfiguration(Delimiter.COMMA, LineEnding.LF, QuoteChar.DOUBLE_QUOTE, true),
            new XlsxFileConfiguration(true),
            new FhirFileConfiguration(""));
    }

    public get fileInfo$(): Observable<FileInformation> {
        if (!this._fileInfoFetched && !this._fileInfoLoading$) {
            this.refreshFileInfo().subscribe({
                error : (error) => {
                    this._fileInfoFetched = false;
                    this.errorHandlingService.addError(error, "Failed to fetch the file information.");
                },
            });
        }

        return this._fileInfo$;
    }

    public refreshFileInfo(): Observable<FileInformation> {
        if (this._fileInfoLoading$) {
            return this._fileInfoLoading$;
        }

        this._fileInfoLoading$ = this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.fetchFileInformation(projectId)),
            tap(value => {
                this.setFileInfo(value);
            }),
            finalize(() => {
                this._fileInfoLoading$ = null;
            }),
            shareReplay({bufferSize: 1, refCount: false}),
        );

        return this._fileInfoLoading$;
    }

    public setFileInfo(fileInfo: FileInformation): void {
        this._fileInfoFetched = true;
        this._fileInfoSubject.next(fileInfo);
    }

    public get dataSourceConfiguration$(): Observable<DataSourceConfiguration> {
        if (!this._dataSourceConfigurationFetched && !this._dataSourceConfigurationLoading$) {
            this.refreshDataSourceConfiguration().subscribe({
                error : (error) => {
                    if (error.error.errorCode === "PLATFORM_1_8_10") {
                        const config = new DataSourceConfiguration(DataSourceType.LOCAL, null);
                        this._dataSourceConfigurationSubject.next(config);
                    } else {
                        this._fileInfoFetched = false;
                        this.errorHandlingService.addError(error, "Failed to fetch the data source configuration.");
                    }
                }
            });
        }
        return this._dataSourceConfiguration$;
    }

    public refreshDataSourceConfiguration(): Observable<DataSourceConfiguration> {
        if (this._dataSourceConfigurationLoading$) {
            return this._dataSourceConfigurationLoading$;
        }

        this._dataSourceConfigurationLoading$ = this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.fetchDataSourceConfiguration(projectId)),
            tap(value => {
                this._dataSourceConfigurationFetched = true;
                this._dataSourceConfigurationSubject.next(value);
            }),
            finalize(() => {
                this._dataSourceConfigurationLoading$ = null;
            }),
            shareReplay({bufferSize: 1, refCount: false}),
        );

        return this._dataSourceConfigurationLoading$;
    }

    /**
     * Uploads the data source configuration contained in the file configuration object to the server.
     *
     * @param dataSourceConfiguration The file configuration containing the data source configuration.
     * @return An empty observable.
     */
    public uploadDataSourceConfiguration(dataSourceConfiguration: DataSourceConfiguration): Observable<FileInformation> {
        this.setDataSourceConfiguration(dataSourceConfiguration);

        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postDataSourceConfiguration(projectId, dataSourceConfiguration)),
            tap(value => this.setFileInfo(value)),
        );
    }

    private setDataSourceConfiguration(dataSourceConfiguration: DataSourceConfiguration) {
        this._dataSourceConfigurationFetched = true;
        this._dataSourceConfigurationSubject.next(dataSourceConfiguration);
    }

    public get fileConfiguration$(): Observable<FileConfiguration> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.fetchFileConfiguration(projectId)),
            tap(value => this.fileConfiguration = value),
            share(),
        );
    }

    public invalidateCache() {
        this._fileInfoFetched = false;
        this._fileInfoLoading$ = null;

        this._dataSourceConfigurationFetched = false;
        this._dataSourceConfigurationLoading$ = null;
    }

    public getFileConfiguration(): FileConfiguration {
        return this.fileConfiguration;
    }

	public setFileConfiguration(value: FileConfiguration) {
		this.fileConfiguration = value;
	}

    public uploadFile(file: File): Observable<FileInformation> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postFile(projectId, file)),
            tap(value => {
                this.setFileInfo(value);
            }),
        );
    }

    /**
     * Stores the given file configuration.
     *
     * @param fileConfiguration The file configuration.
     */
    public uploadFileConfiguration(fileConfiguration: FileConfiguration): Observable<FileInformation> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postFileConfiguration(projectId, fileConfiguration)),
            tap(value => {
                this.setFileInfo(value);
            }),
        );
    }

    /**
     * Estimates the file configuration for the currently stored file.
     *
     * @return The estimation result.
     */
    public estimateFileConfiguration(): Observable<FileConfigurationEstimation> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postFileEstimationAction(projectId)),
            tap(value => this.fileConfiguration = value.estimation),
        );
    }

    /**
     * Retrieves the data from the configured server.
     *
     * @return The estimation result.
     */
    public retrieveFile(): Observable<FileInformation> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postFileRetrievalAction(projectId)),
            tap(value => {
                this.setFileInfo(value);
            }),
        );
    }

    private baseUrl(projectId: string): string {
        return environments.apiUrl + "/api/project/" + projectId +  "/data/file";
    }

    private fetchDataSourceConfiguration(projectId: string): Observable<DataSourceConfiguration> {
        return this.httpClient.get<DataSourceConfiguration>(this.baseUrl(projectId) + "/source");
    }

    private fetchFileConfiguration(projectId: string): Observable<FileConfiguration> {
        return this.httpClient.get<FileConfiguration>(this.baseUrl(projectId) + "/configuration");
    }

    private fetchFileInformation(projectId: string): Observable<FileInformation> {
        return this.httpClient.get<FileInformation>(this.baseUrl(projectId));
    }

    private postDataSourceConfiguration(projectId: string, dataSourceConfiguration: DataSourceConfiguration): Observable<FileInformation> {
        const formData = new FormData();
        const fileConfigString = JSON.stringify(dataSourceConfiguration);
        formData.append("dataSourceConfiguration", fileConfigString);

        return this.httpClient.post<FileInformation>(this.baseUrl(projectId) + "/source", formData);
    }

    private postFile(projectId: string, file: File): Observable<FileInformation> {
        const formData = new FormData();
        formData.append("file", file);

        return this.httpClient.post<FileInformation>(this.baseUrl(projectId), formData);
    }

    private postFileConfiguration(projectId: string, fileConfiguration: FileConfiguration): Observable<FileInformation> {
        const formData = new FormData();
        const fileConfigString = JSON.stringify(fileConfiguration);
        formData.append("fileConfiguration", fileConfigString);

        return this.httpClient.post<FileInformation>(this.baseUrl(projectId) + "/configuration", formData);
    }

    private postFileEstimationAction(projectId: string): Observable<FileConfigurationEstimation> {
        return this.httpClient.post<FileConfigurationEstimation>(this.baseUrl(projectId) + "/estimate", {});
    }

    private postFileRetrievalAction(projectId: string): Observable<FileInformation> {
        return this.httpClient.post<FileInformation>(this.baseUrl(projectId) + "/retrieve", {});
    }

}
