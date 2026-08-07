import { HttpClient } from "@angular/common/http";
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { NavigationService } from "@core/services/navigation.service";
import { TitleService } from "@core/services/title-service.service";
import { NavigationKey } from "@shared/model/navigation";
import { UserInfo, UserRole } from "@shared/model/user";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { UserService } from "@shared/services/user.service";
import { combineLatest, Observable, of, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
  selector: 'app-admin-page',
  standalone: false,
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.less'
})
export class AdminPageComponent implements OnInit {

    protected readonly userTableColumns = ['username', 'roleUser', 'roleApi', 'roleAdmin', 'roleMonitoring'];
    protected readonly UserRole = UserRole;

    protected pageData$: Observable<{
        currentUser: string,
        users: UserInfo[],
    }>;

    protected dataSource = new MatTableDataSource();

    @ViewChild(MatSort)
    protected set sort(sort: MatSort) {
        this.dataSource.sort = sort;
    }

    @ViewChild(MatPaginator)
    protected set paginator(paginator: MatPaginator) {
        this.dataSource.paginator = paginator;
    }

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
        private readonly navigationService: NavigationService,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.navigationService.setNavigationKey(NavigationKey.ADMIN);
        this.titleService.setPageTitle("Administration - Security");
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            currentUser: of(this.getCurrentUser()),
            users: this.fetchUsers$(),
        }).pipe(
            tap(data => {
                this.dataSource.data = data.users;
            }),
        );
    }

    protected onRoleChange(username: string, role: UserRole, source: MatCheckbox): void {
        const isChecked = source.checked;

        this.http.patch(this.baseUrl() + "/users/roles", {
            username: username,
            roles: [role],
            action: isChecked ? "ADD" : "REMOVE",
        }).subscribe({
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to update user role");
                source.checked = !isChecked;
            }
        });
    }

    protected applyFilterEvent(event: Event): void {
        const filterValue = (event.target as HTMLInputElement).value;
        this.applyFilterValue(filterValue);
    }

    protected applyFilterValue(filterValue: string): void {
        this.dataSource.filter = filterValue.trim().toLowerCase();

        if (this.dataSource.paginator) {
            this.dataSource.paginator.firstPage();
        }
    }

    protected getCurrentUser(): string {
        return this.userService.getUser().userInfo.username;
    }

    private fetchUsers$(): Observable<UserInfo[]> {
        return this.http.get<UserInfo[]>(this.baseUrl() + "/users");
    }

    private baseUrl(): string {
        return environments.apiUrl + "/api/admin";
    }
}
