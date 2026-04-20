import { bootstrapAppComponentlication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app';

bootstrapAppComponentlication(AppComponent, appConfig)
  .catch((err) => console.error(err));
