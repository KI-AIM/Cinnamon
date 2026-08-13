import { Component } from '@angular/core';
import { NavigationService } from "@core/services/navigation.service";
import { NavigationKey } from "@shared/model/navigation";

/**
 * Shell of the administration interface.
 * Only renders the page selected in the navigation, see {@link AdminPageConfiguration}.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-admin-shell',
    standalone: false,
    templateUrl: './admin-shell.component.html',
})
export class AdminShellComponent {

    constructor(
        private readonly navigationService: NavigationService,
    ) {
        this.navigationService.setNavigationKey(NavigationKey.ADMIN);
    }

}
