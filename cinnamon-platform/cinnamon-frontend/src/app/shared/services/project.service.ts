import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { Project } from "@shared/model/project";
import {
    BehaviorSubject,
    distinctUntilChanged,
    filter,
    map,
    Observable, switchMap,
    take,
    tap
} from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class ProjectService {

    /**
     * Subject that emits the currently opened project.
     * Null if no project is opened or the project is closed.
     */
    private _projectSubject: BehaviorSubject<Project | null>;

    constructor(
        private readonly http: HttpClient,
    ) {
        this._projectSubject = new BehaviorSubject<Project | null>(null);
    }

    public get project(): Project | null {
        return this._projectSubject.value;
    }

    public get project$(): Observable<Project | null> {
        return this._projectSubject.asObservable();
    }

    public get projectId$(): Observable<string | null> {
        return this.project$.pipe(
            map(project => project?.id || null),
            distinctUntilChanged(),
        );
    }

    public get projectIdRequired$(): Observable<string> {
        return this.projectId$.pipe(
            filter(id => id != null),
        );
    }

    public get projectIdRequiredOnce$(): Observable<string> {
        return this.projectIdRequired$.pipe(
            take(1),
        );
    }

    public get projectClosed$(): Observable<null> {
        return this._projectSubject.asObservable().pipe(
            filter(project => project === null)
        );
    }

    public openProjectId(projectId: string) {
        return this.fetchProject(projectId).pipe(
            tap(value => this._projectSubject.next(value)),
        );
    }

    public closeProject() {
        this._projectSubject.next(null);
    }

    public fetchProject(id: string): Observable<Project> {
        return this.http.get<Project>(this.baseUrl(id));
    }

    public deleteProject(username: string, password: string): Observable<void> {
        return this.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postDelete(projectId, username, password)),
        );
    }

    private postDelete(projectId: string, username: string, password: string): Observable<void> {
        const formData = new FormData();
        formData.append("username", username);
        formData.append("password", password);
        return this.http.delete<void>(this.baseUrl(projectId), {body: formData});
    }

    private baseUrl(projectId: string): string {
        return `${environments.apiUrl}/api/project/${projectId}`;
    }

}
