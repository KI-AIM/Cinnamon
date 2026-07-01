import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Router } from "@angular/router";
import { AppNotification, NotificationService, NotificationType } from "@core/services/notification.service";
import { Project } from "@shared/model/project";
import { User } from "@shared/model/user";
import { BehaviorSubject, Observable, Subject, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: "root",
})
export class UserService {
    /**
     * Cached email for the login and register page.
     */
    public cachedEmailInput: string | null = null;
    /**
     * Cached password for the login and register page.
     */
    public cachedPasswordInput: string | null = null;

    private readonly baseURL = environments.apiUrl + "/api/user";
    private readonly USER_KEY = "user";
    private user: User;

    private loginSubject: Subject<void> = new Subject<void>();
    private logoutSubject: Subject<void> = new Subject<void>();

    private projectListSubject: BehaviorSubject<Project[]> = new BehaviorSubject<Project[]>([]);

    constructor(
        private readonly http: HttpClient,
        private readonly notificationService: NotificationService,
        private readonly router: Router,
    ) {
        const storedUser = sessionStorage.getItem(this.USER_KEY);
        if (storedUser !== null) {
            this.user = JSON.parse(storedUser);
        } else {
            this.user = new User(false, "", "");
        }
    }

    getUser(): User {
        return this.user;
    }

    isAuthenticated(): boolean {
        return this.user.authenticated;
    }

    /**
     * Returns an observable that emits when the user logs in.
     */
    public login$(): Observable<void> {
        return this.loginSubject.asObservable();
    }

    public logout$(): Observable<void> {
        return this.logoutSubject.asObservable();
    }

    login(
        credentials: { email: string; password: string }
    ): Observable<any> {
        this.user = new User(false, "", "");

        const token = btoa(credentials.email + ":" + credentials.password);
        const headers = new HttpHeaders(
            credentials ? {authorization: "Basic " + token} : {}
        );

        return this.http.get<any>(this.baseURL + "/login", {headers: headers}).pipe(
            tap(data => {
                if (typeof data === "boolean" && data) {
                    this.user = new User(true, credentials.email, token);
                    sessionStorage.setItem(this.USER_KEY, JSON.stringify(this.user));
                    this.loginSubject.next();
                }
            }),
        );
    }


    register(request: {
        email: string;
        password: string;
        passwordRepeated: string;
    }): Observable<any> {
        return this.http.post(this.baseURL + "/register", request);
    }

    /**
     * Deletes the currently authenticated user.
     * @param email The email of the user.
     * @param password The password of the user.
     */
    public delete(email: string, password: string): Observable<void> {
        const formData = new FormData();
        formData.append("email", email);
        formData.append("password", password);

        return this.http.delete<void>(this.baseURL + "/delete", {body: formData});
    }

    /**
     * Logs out the user, redirects to the login page, and displays a message based on the given mode.
     * @param mode The mode defining the displayed message.
     */
    public logout(mode: LogoutMode) {
        const project = this.user.email || null;

        sessionStorage.removeItem(this.USER_KEY)
        this.user = new User(false, "", "");

        let message = "";
        let type: NotificationType = "success";
        switch (mode) {
            case "close":
                message = "Successfully closed project";
                break;
            case "delete":
                message = "Successfully deleted project";
                break;
            case "expired":
                message = "Session expired";
                type = "failure";
                break;
        }

        this.logoutSubject.next();

        this.router.navigate(['open']).then(() => {
            const notification = new AppNotification(message, type);
            notification.project = project;
            this.notificationService.addNotification(notification);
        });
    }

    public getProjectsForCurrentUser$(): Observable<Project[]> {
        this.refreshProjectsForCurrentUser$().subscribe();
        return this.projectListSubject.asObservable();
    }

    public refreshProjectsForCurrentUser$(): Observable<Project[]> {
        return this.fetchProjectsForCurrentUser$().pipe(
            tap((projects) => {
                this.projectListSubject.next(projects);
            }),
        );
    }

    private fetchProjectsForCurrentUser$(): Observable<Project[]> {
        return this.http.get<Project[]>(environments.apiUrl + "/api/user/-/projects");
    }

    public createProjectForCurrentUser(projectName: string): Observable<Project> {
        const formData = new FormData();
        formData.append("projectName", projectName);

        return this.http.post<Project>(environments.apiUrl + "/api/user/-/projects", formData);
    }

}

export type LogoutMode = "close" | "delete" | "expired";
