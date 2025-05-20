import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true, // ✅ Mark it as standalone
  imports: [RouterModule], // ✅ Import RouterModule so routing works
  template: `<router-outlet></router-outlet>`, // ✅ Ensure routing works
})
export class AppComponent { }
