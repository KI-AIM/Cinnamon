import { Injectable } from "@angular/core";
import {
	ActivatedRouteSnapshot,
	CanActivate,
	Router,
	RouterStateSnapshot,
	UrlTree,
} from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { Observable } from "rxjs";
import { UserService } from "src/app/shared/services/user.service";

@Injectable({
	providedIn: "root",
})
export class AuthGuard implements CanActivate {
	constructor(
        private readonly notificationService: NotificationService,
		private readonly userService: UserService,
		private readonly router: Router
	) {}

	canActivate(
		route: ActivatedRouteSnapshot,
		state: RouterStateSnapshot
	):
		| Observable<boolean | UrlTree>
		| Promise<boolean | UrlTree>
		| boolean
		| UrlTree {
		if (!this.userService.isAuthenticated()) {
            this.router.navigate(["open"]).then(() => {
                const notification = new AppNotification("You must authenticate before accessing this page", "failure");
                this.notificationService.addNotification(notification);
            });
			return false;
		}
		return true;
	}
}
