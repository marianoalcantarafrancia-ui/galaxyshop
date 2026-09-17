import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { KeycloakService } from '../../core/keycloak.service';

@Component({
  selector: 'app-sin-permiso',
  imports: [RouterLink],
  template: `
    <div class="tarjeta" style="max-width:34rem;margin:3rem auto;text-align:center">
      <h1>Acceso no autorizado</h1>
      <p class="subtitulo">
        Tu cuenta <strong>{{ keycloak.usuario() }}</strong> no tiene los roles que
        esta seccion exige.
      </p>
      <p style="font-size:.85rem;color:var(--texto-suave)">Roles de tu token:</p>
      <div>
        @for (r of keycloak.roles(); track r) {
          <span class="insignia insignia--rol">{{ r }}</span>
        }
      </div>
      <p style="margin-top:1.5rem"><a class="boton boton--primario" routerLink="/">Volver al inicio</a></p>
    </div>
  `
})
export class SinPermiso {
  readonly keycloak = inject(KeycloakService);
}
