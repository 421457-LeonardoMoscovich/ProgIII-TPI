import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app';

if (!('global' in globalThis)) {
  (globalThis as typeof globalThis & { global: typeof globalThis }).global = globalThis;
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err: unknown) => console.error(err));
