import { Component, OnInit } from '@angular/core';
import { NavigationService } from "@core/services/navigation.service";
import { TitleService } from "@core/services/title-service.service";
import { NavigationKey } from "@shared/model/navigation";

@Component({
  selector: 'app-admin-page',
  standalone: false,
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.less'
})
export class AdminPageComponent implements OnInit {

    protected readonly dataSource = [
        {username: "user1", roles: ["admin"], status: "active"},
        {username: "user2", roles: ["user"], status: "active"},
        {username: "user3", roles: ["user", "api"], status: "active"},
    ];

    constructor(
        private readonly navigationService: NavigationService,
        private readonly titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        this.titleService.setPageTitle("Administration - Security");
        this.navigationService.setNavigationKey(NavigationKey.ADMIN);
    }

}
