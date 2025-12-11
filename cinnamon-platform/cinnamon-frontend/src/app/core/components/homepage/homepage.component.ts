import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { TitleService } from "@core/services/title-service.service";
import { LogoutMode, UserService } from "@shared/services/user.service";

/**
 * Homepage of the platform.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-homepage',
  standalone: false,
  templateUrl: './homepage.component.html',
})
export class HomepageComponent implements OnInit {
    protected mode: LogoutMode | null;
    protected isAuthenticated = false;

    public constructor(
        private readonly activateRoute: ActivatedRoute,
        private readonly titleService: TitleService,
        private readonly userService: UserService
    ) {
        this.titleService.setPageTitle("Cinnamon – The Data Protection Platform");
    }

    public ngOnInit(): void {
        this.isAuthenticated = this.userService.isAuthenticated();

        this.activateRoute.queryParams.subscribe((params) => {
            if (params["mode"]) {
                this.mode = params["mode"];
            } else {
                this.mode = null;
            }
        });
    }

    /**
     * Closes the current project.
     */
    protected onLogout() {
        this.userService.logout("close");
    }
}
