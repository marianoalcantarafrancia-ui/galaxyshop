import { Injectable, signal, computed } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

/** Perfil efectivo del usuario, tal como lo devuelve GET /api/me. */
export interface Perfil {
  usuario: string;
  nombre: string;
  email?: string;
  sede?: string;
  categoria: string;
  grupos: string[];
  roles: string[];
  permisosGranulares: PermisoGranular[];
}

export interface PermisoGranular {
  rsname: string;
  scopes?: string[];
}

/**
 * Integracion con Keycloak mediante keycloak-js.
 *
 * Flujo Authorization Code + PKCE (S256): el SPA es un cliente publico, no
 * guarda secretos y nunca ve la contrasena del usuario, que sigue viviendo
 * solo en Keycloak.
 */
@Injectable({ providedIn: 'root' })
export class KeycloakService {

  private keycloak!: Keycloak;

  readonly autenticado = signal(false);
  readonly perfil = signal<Perfil | null>(null);

  readonly roles = computed(() => this.perfil()?.roles ?? []);
  readonly usuario = computed(() => this.perfil()?.usuario ?? 'invitado');

  /** Arranca Keycloak antes de que Angular pinte la aplicacion. */
  async iniciar(): Promise<void> {
    this.keycloak = new Keycloak({
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId
    });

    // check-sso no fuerza el login: permite que el visitante vea el escaparate
    // publico y solo se identifique cuando lo necesita.
    const ok = await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html'
    });

    this.autenticado.set(ok);

    if (ok) {
      // Renueva el access token cuando le quedan menos de 60 segundos.
      setInterval(() => this.keycloak.updateToken(60).catch(() => this.logout()), 30_000);
    }
  }

  login(): void {
    this.keycloak.login({ redirectUri: window.location.origin + '/' });
  }

  registrarse(): void {
    this.keycloak.register({ redirectUri: window.location.origin + '/' });
  }

  logout(): void {
    this.perfil.set(null);
    this.autenticado.set(false);
    this.keycloak.logout({ redirectUri: window.location.origin + '/' });
  }

  /** Abre la consola de cuenta de Keycloak (cambio de contrasena, sesiones). */
  gestionarCuenta(): void {
    this.keycloak.accountManagement();
  }

  get token(): string | undefined {
    return this.keycloak?.token;
  }

  /**
   * Comprueba un rol del usuario.
   *
   * Se consulta el TOKEN, que keycloak-js ya tiene parseado en cuanto
   * termina init(). Es importante que no dependa de this.perfil(), que llega
   * por HTTP desde /api/me: un guard puede evaluarse antes de esa respuesta y
   * denegaria el paso a un usuario que si tiene el rol.
   *
   * Keycloak reparte los roles en dos sitios y hay que mirar los dos:
   *   realm_access.roles                     -> gs-admin, gs-auditor...
   *   resource_access.shop-backend.roles     -> report-read, catalog-write...
   */
  tieneRol(rol: string): boolean {
    if (this.keycloak?.authenticated) {
      if (this.keycloak.hasRealmRole(rol)) {
        return true;
      }
      if (this.keycloak.hasResourceRole(rol, environment.keycloak.resourceClientId)) {
        return true;
      }
    }
    // Reserva: el perfil de /api/me, por si el token aun no estuviera listo
    return this.roles().includes('ROLE_' + rol);
  }

  tieneAlgunRol(...roles: string[]): boolean {
    return roles.some(r => this.tieneRol(r));
  }

  /**
   * Comprueba un permiso de Authorization Services.
   * El backend ya devolvio el RPT en /api/me, asi que la UI puede ocultar
   * botones sin volver a preguntar a Keycloak.
   */
  puede(recurso: string, scope: string): boolean {
    const permisos = this.perfil()?.permisosGranulares ?? [];
    return permisos.some(p => p.rsname === recurso && (p.scopes ?? []).includes(scope));
  }
}
