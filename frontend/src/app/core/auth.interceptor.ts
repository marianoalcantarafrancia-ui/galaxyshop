import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { KeycloakService } from './keycloak.service';
import { environment } from '../../environments/environment';

/**
 * Adjunta el bearer token a las llamadas dirigidas a la API.
 * Las rutas publicas funcionan igual sin token.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(KeycloakService);
  const token = keycloak.token;

  if (token && req.url.startsWith(environment.apiUrl)) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
