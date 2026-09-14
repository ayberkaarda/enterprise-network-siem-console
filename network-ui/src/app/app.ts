import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root shell.
 *
 * The console itself is no longer rendered from here: it is a routed,
 * lazily loaded page behind an authentication guard, so an unauthenticated
 * visitor gets the login screen instead of a console that fires a burst of
 * requests it cannot complete. Everything this component does is host the
 * outlet the router paints into.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
