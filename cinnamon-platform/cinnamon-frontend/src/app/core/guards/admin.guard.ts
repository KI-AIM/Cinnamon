import { Injectable } from "@angular/core";
import {
	ActivatedRouteSnapshot,
	CanActivate,
	Router,
	RouterStateSnapshot,
	UrlTree,
} from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { UserRole } from "@shared/model/user";
import { Observable } from "rxjs";
import { UserService } from "src/app/shared/services/user.service";

@Injectable({
	providedIn: "root",
})
export class AdminGuard implements CanActivate {
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
		if (!this.userService.getUser().userInfo.roles.includes(UserRole.ROLE_ADMIN)) {
			this.router.navigate(["/user/-/home"]).then(() => {
				const notification = new AppNotification("You must be an administrator to access this page", "failure");
				this.notificationService.addNotification(notification);
			});
			return false;
		}
		return true;
	}
}
