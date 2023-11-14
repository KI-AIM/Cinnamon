import { Component } from '@angular/core';
import { Mode } from '../../enums/mode';
import { Steps } from '../../enums/steps';
import { StateManagementService } from '../../services/state-management.service';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.less']
})
export class NavigationComponent {
  Mode = Mode; 
  Steps = Steps; 

  constructor(public stateManagement: StateManagementService) {

  }

}
