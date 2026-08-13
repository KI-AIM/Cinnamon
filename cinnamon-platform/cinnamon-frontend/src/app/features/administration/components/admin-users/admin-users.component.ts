import { Component, OnInit, ViewChild } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { TitleService } from "@core/services/title-service.service";
import { UserInfo, UserRole } from "@shared/model/user";
import { AdminService } from "@shared/services/admin.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { UserService } from "@shared/services/user.service";
import { combineLatest, Observable, of, tap } from "rxjs";

/**
 * Administration page for managing the roles of the users of the application.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-admin-users',
    standalone: false,
    templateUrl: './admin-users.component.html',
    styleUrl: './admin-users.component.less'
})
export class AdminUsersComponent implements OnInit {

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
        private readonly adminService: AdminService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("Administration - Users");
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            currentUser: of(this.getCurrentUser()),
            users: this.adminService.getAllUsers(),
        }).pipe(
            tap(data => {
                this.dataSource.data = data.users;
            }),
        );
    }

    protected onRoleChange(username: string, role: UserRole, source: MatCheckbox): void {
        const isChecked = source.checked;

        this.adminService.updateUserRoles(username, [role], isChecked ? "ADD" : "REMOVE").subscribe({
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

}
