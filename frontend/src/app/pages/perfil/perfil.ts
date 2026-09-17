import { Component, inject } from '@angular/core';
import { KeycloakService } from '../../core/keycloak.service';

/** NIVEL PRIVADO: muestra la identidad efectiva que la API leyo del token. */
@Component({
  selector: 'app-perfil',
  templateUrl: './perfil.html'
})
export class Perfil {

  readonly keycloak = inject(KeycloakService);

  rolesDeRealm(): string[] {
    return this.keycloak.roles()
      .filter(r => r.startsWith('ROLE_gs-'))
      .map(r => r.replace('ROLE_', ''));
  }

  rolesDeCliente(): string[] {
    return this.keycloak.roles()
      .filter(r => r.startsWith('ROLE_') && !r.startsWith('ROLE_gs-')
                && !r.startsWith('ROLE_default-') && !r.startsWith('ROLE_offline')
                && !r.startsWith('ROLE_uma_'))
      .map(r => r.replace('ROLE_', ''));
  }

  scopesOauth(): string[] {
    return this.keycloak.roles().filter(r => r.startsWith('SCOPE_')).map(r => r.replace('SCOPE_', ''));
  }
}
