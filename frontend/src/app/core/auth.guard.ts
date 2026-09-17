import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { KeycloakService } from './keycloak.service';

/** Exige sesion iniciada; si no la hay, envia al login de Keycloak. */
export const authGuard: CanActivateFn = () => {
  const keycloak = inject(KeycloakService);
  if (keycloak.autenticado()) {
    return true;
  }
  keycloak.login();
  return false;
};

/**
 * Exige ademas uno de los roles indicados en la ruta:
 *   { path: 'reportes', canActivate: [rolesGuard], data: { roles: ['gs-auditor'] } }
 */
export const rolesGuard: CanActivateFn = (route) => {
  const keycloak = inject(KeycloakService);
  const router = inject(Router);

  if (!keycloak.autenticado()) {
    keycloak.login();
    return false;
  }

  const requeridos = (route.data?.['roles'] as string[]) ?? [];
  if (requeridos.length === 0 || keycloak.tieneAlgunRol(...requeridos)) {
    return true;
  }
  return router.createUrlTree(['/sin-permiso']);
};
