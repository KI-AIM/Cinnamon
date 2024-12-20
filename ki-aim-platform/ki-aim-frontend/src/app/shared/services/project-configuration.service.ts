import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { BehaviorSubject, Observable, shareReplay, tap } from "rxjs";
import { ProjectSettings } from "../model/project-settings";

@Injectable({
    providedIn: 'root'
})
export class ProjectConfigurationService {

    private readonly baseUrl: string = environments.apiUrl + "/api/project";

    private _projectSettings$: Observable<ProjectSettings> | null = null;
    private projectSettingsSubject: BehaviorSubject<ProjectSettings> | null = null;

    constructor(
        private readonly http: HttpClient,
    ) {
    }

    private initializeProjectSettings(): void {
        if (!this.projectSettingsSubject) {
            this.projectSettingsSubject = new BehaviorSubject<ProjectSettings>(new ProjectSettings());
            this._projectSettings$ = this.fetchProjectSettings().pipe(
                tap(value => {
                    this.projectSettingsSubject?.next(value);
                }),
                shareReplay(1),
            );
        }
    }

    public get projectSettings$(): Observable<ProjectSettings> {
        this.initializeProjectSettings();
        return this._projectSettings$!;
    }

    public setProjectSettings(value: ProjectSettings): Observable<void> {
        this.projectSettingsSubject!.next(value);
        return this.putProjectSettings(value);
    }

    private fetchProjectSettings(): Observable<ProjectSettings> {
        return this.http.get<ProjectSettings>(this.baseUrl + "/configuration");
    }

    private putProjectSettings(projectSettings: ProjectSettings): Observable<void> {
        return this.http.put<void>(this.baseUrl + "/configuration", projectSettings);
    }

}

