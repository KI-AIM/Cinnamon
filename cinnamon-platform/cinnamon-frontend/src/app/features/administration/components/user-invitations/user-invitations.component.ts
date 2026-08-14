import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { UserInvitationInfo, UserRole } from "@shared/model/user";
import { Observable, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
  selector: 'app-user-invitations',
  standalone: false,
  templateUrl: './user-invitations.component.html',
  styleUrl: './user-invitations.component.less'
})
export class UserInvitationsComponent implements OnInit {

    protected readonly UserRole = UserRole;
    protected readonly tableColumns = ['username', 'status', 'actions'];

    protected dataSource = new MatTableDataSource();
    protected invitations$: Observable<UserInvitationInfo[]>;

    public constructor(
        private readonly httpClient: HttpClient,
    ) {
    }

    public ngOnInit(): void {
        this.invitations$ = this.fetchInvitations().pipe(
            tap(invitations => {
                this.dataSource.data = invitations;
            }),
        );
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

    private fetchInvitations(): Observable<UserInvitationInfo[]> {
        return this.httpClient.get<UserInvitationInfo[]>(environments.apiUrl + '/api/admin/invitations');
    }
}
