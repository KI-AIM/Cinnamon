import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { AbstractControl, ValidationErrors, ValidatorFn } from "@angular/forms";
import { Router } from "@angular/router";
import { AppNotification, NotificationService, NotificationType } from "@core/services/notification.service";
import { Project } from "@shared/model/project";
import { User, UserInfo } from "@shared/model/user";
import { PasswordRequirements } from "@shared/services/app-config.service";
import { BehaviorSubject, from, Observable, Subject, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: "root",
})
export class UserService {
    /**
     * Cached username for the login and register page.
     */
    public cachedUsernameInput: string | null = null;
    /**
     * Cached password for the login and register page.
     */
    public cachedPasswordInput: string | null = null;

    private readonly baseURL = environments.apiUrl + "/api/user";
    private readonly USER_KEY = "user";

    private userSubject: BehaviorSubject<User>;
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
            this.userSubject = new BehaviorSubject<User>(JSON.parse(storedUser) as User);
        } else {
            this.userSubject = new BehaviorSubject<User>(this.createLoggedOutUser());
        }
    }

    getUser(): User {
        return this.userSubject.value;
    }

    public get user$(): Observable<User> {
        return this.userSubject.asObservable();
    }

    isAuthenticated(): boolean {
        return this.getUser().authenticated;
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

    public routeToUser$(): Observable<boolean> {
        let routing;

        if (this.isAuthenticated()) {
            routing = this.router.navigate(["/user/-/home"]);
        } else {
            routing = this.router.navigate(["/"]);
        }

        return from(routing);
    }

    login(
        credentials: { username: string; password: string }
    ): Observable<any> {
        const token = btoa(credentials.username + ":" + credentials.password);
        const headers = new HttpHeaders(
            credentials ? {authorization: "Basic " + token} : {}
        );

        return this.http.get<UserInfo>(this.baseURL + "/login", {headers: headers}).pipe(
            tap(userInfo => {
                this.setUser(this.createLoggedInUser(credentials.username, credentials.password, userInfo));
                this.loginSubject.next();
            }),
        );
    }


    register(request: {
        username: string;
        password: string;
        passwordRepeated: string;
    }): Observable<any> {
        return this.http.post(this.baseURL + "/register", request);
    }

    /**
     * Deletes the currently authenticated user.
     * @param username The username of the user.
     * @param password The password of the user.
     */
    public delete(username: string, password: string): Observable<void> {
        const formData = new FormData();
        formData.append("username", username);
        formData.append("password", password);

        return this.http.delete<void>(this.baseURL + "/-/delete", {body: formData});
    }

    /**
     * Logs out the user, redirects to the login page, and displays a message based on the given mode.
     * @param mode The mode defining the displayed message.
     */
    public logout(mode: LogoutMode) {
        const user = this.getUser().userInfo.username || null;

        this.setUser(this.createLoggedOutUser());

        let message = "";
        let type: NotificationType = "success";
        switch (mode) {
            case "close":
                message = "Successfully logged out";
                break;
            case "delete":
                message = "Successfully deleted account";
                break;
            case "expired":
                message = "Session expired";
                type = "failure";
                break;
        }

        this.logoutSubject.next();

        this.router.navigate(['/']).then(() => {
            const notification = new AppNotification(message, type);
            notification.user = user;
            this.notificationService.addNotification(notification);
        });
    }

    public updateUsername(newUsername: string, currentPassword: string): Observable<UserInfo> {
        const body = {
            newUsername,
            currentPassword
        };

        return this.http.post<UserInfo>(this.baseURL + "/-/update-username", body).pipe(
            tap(userInfo => {
                this.setUser(this.createLoggedInUser(newUsername, currentPassword, userInfo));
            }),
        );
    }

    public updatePassword(currentPassword: string, newPassword: string, newPasswordRepeated: string): Observable<UserInfo> {
        const body = {
            currentPassword,
            newPassword,
            newPasswordRepeated
        };

        return this.http.post<UserInfo>(this.baseURL + "/-/update-password", body).pipe(
            tap(userInfo => {
                this.setUser(this.createLoggedInUser(this.getUser().userInfo.username, newPassword, userInfo));
            }),
        );
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
        return this.http.get<Project[]>(this.baseURL + "/-/projects");
    }

    public createProjectForCurrentUser(projectName: string): Observable<Project> {
        const formData = new FormData();
        formData.append("projectName", projectName);

        return this.http.post<Project>(this.baseURL + "/-/projects", formData);
    }

    private createLoggedOutUser(): User {
        return new User(false, new UserInfo(), "");
    }

    private createLoggedInUser(username: string, password: string, userInfo: UserInfo): User {
        const token = btoa(username + ":" + password);
        return new User(true, userInfo, token);
    }

    private setUser(user: User): void {
        sessionStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.userSubject.next(user);
    }

    /**
     * Creates a password validator for the given password requirements.
     * @param passwordRequirements The password requirements.
     * @private
     */
    public passwordRequirementsValidator(passwordRequirements: PasswordRequirements): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            if (typeof control.value !== "string") {
                return null;
            }

            const hasLength = control.value.length >= passwordRequirements.minLength

            const constraints = passwordRequirements.constraints;
            let hasLowercase = !constraints.includes('LOWERCASE');
            let hasDigit = !constraints.includes('DIGIT');
            let hasSpecialChar = !constraints.includes('SPECIAL_CHAR');
            let hasUppercase = !constraints.includes('UPPERCASE');

            for (let i = 0; i < control.value.length; i++) {
                const c = control.value.charAt(i);

                if (/\p{N}/u.test(c)) {
                    hasDigit = true;
                } else if (/\p{Ll}/u.test(c)) {
                    hasLowercase = true;
                } else if (/\p{Lu}/u.test(c)) {
                    hasUppercase = true;
                } else {
                    hasSpecialChar = true;
                }
            }

            const v: Record<string, any> = {};
            if (!hasLength) {
                v['length'] = {minLength: passwordRequirements.minLength};
            }
            if (!hasDigit) {
                v['digit'] = {};
            }
            if (!hasLowercase) {
                v['lowercase'] = {};
            }
            if (!hasUppercase) {
                v['uppercase'] = {};
            }
            if (!hasSpecialChar) {
                v['specialChar'] = {};
            }

            return hasLength && hasLowercase && hasDigit && hasSpecialChar && hasUppercase ? null : v;
        }
    }

    /**
     * Validates that the password and passwordRepeated inputs match.
     */
    public passwordMatchesValidator(passwordField: string, passwordRepeatedField: string ): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const passwordRepeatedControl = control.get(passwordRepeatedField)!;

            const password = control.get(passwordField)!.value;
            const passwordRepeated = passwordRepeatedControl.value;

            const error = password !== passwordRepeated ? {passwordMatch: true} : null;

            passwordRepeatedControl.setErrors(error);
            passwordRepeatedControl.markAsTouched({onlySelf: true, emitEvent: false});

            return error;
        }
    }

}

export type LogoutMode = "close" | "delete" | "expired";
