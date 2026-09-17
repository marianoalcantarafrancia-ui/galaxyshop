import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { KeycloakService } from './app/core/keycloak.service';

/**
 * Keycloak se inicializa ANTES de arrancar Angular.
 *
 * Asi, cuando el primer componente se pinta, la aplicacion ya sabe si hay
 * sesion activa y no aparece el parpadeo de "invitado -> usuario".
 */
const keycloak = new KeycloakService();

keycloak.iniciar()
  .then(() => bootstrapApplication(App, {
    ...appConfig,
    providers: [
      ...appConfig.providers,
      { provide: KeycloakService, useValue: keycloak }
    ]
  }))
  .catch(err => {
    console.error('No se pudo inicializar Keycloak', err);
    document.body.innerHTML =
      '<div style="font-family:system-ui;padding:3rem;max-width:40rem;margin:0 auto">' +
      '<h1>No se pudo contactar con Keycloak</h1>' +
      '<p>Verifica que el servidor este levantado en <code>http://localhost:8081</code>:</p>' +
      '<pre style="background:#f4f4f5;padding:1rem;border-radius:.5rem">docker compose up -d</pre></div>';
  });
