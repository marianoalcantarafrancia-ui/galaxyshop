import { Routes } from '@angular/router';
import { authGuard, rolesGuard } from './core/auth.guard';

/**
 * Rubrica #5: los tres niveles de acceso del frontend.
 *
 *   PUBLICO   sin guard                  escaparate de la tienda
 *   PRIVADO   authGuard                  pedidos y perfil del usuario
 *   ESPECIAL  rolesGuard + data.roles    reportes y administracion
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/inicio/inicio').then(m => m.Inicio)
  },
  {
    path: 'catalogo',
    loadComponent: () => import('./pages/catalogo/catalogo').then(m => m.Catalogo)
  },
  {
    path: 'pedidos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/pedidos/pedidos').then(m => m.Pedidos)
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/perfil/perfil').then(m => m.Perfil)
  },
  {
    path: 'reportes',
    canActivate: [rolesGuard],
    data: { roles: ['gs-auditor', 'gs-manager', 'gs-admin', 'report-read'] },
    loadComponent: () => import('./pages/reportes/reportes').then(m => m.Reportes)
  },
  {
    path: 'administracion',
    canActivate: [rolesGuard],
    data: { roles: ['gs-admin'] },
    loadComponent: () => import('./pages/administracion/administracion').then(m => m.Administracion)
  },
  {
    path: 'sin-permiso',
    loadComponent: () => import('./pages/inicio/sin-permiso').then(m => m.SinPermiso)
  },
  { path: '**', redirectTo: '' }
];
