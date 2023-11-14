import { Component } from '@angular/core';
import { NavigationComponent } from 'src/app/core/components/navigation/navigation.component';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.less'],
  providers: []
})
export class AppComponent {

  constructor(private titleService: TitleService, stateManagement: StateManagementService) {
  }

  getTitle(): String {
    return this.titleService.getPageTitle(); 
  }
}
