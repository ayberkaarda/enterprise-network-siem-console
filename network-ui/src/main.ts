import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import 'zone.js'; // En üste bu satırı ekliyoruz

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
