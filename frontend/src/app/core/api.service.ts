import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Perfil } from './keycloak.service';

export interface Catalogo { id: string; nombre: string; descripcion?: string; }

export interface Producto {
  id: string; nombre: string; descripcion?: string;
  precioUnitario: number; stock: number; catalogo: string; catalogoId: string;
}

export interface Pedido {
  id: string; cliente: string; producto: string; catalogo: string;
  sede?: string; cantidad: number; precioUnitario: number;
  total: number; estado: string; fecha: string;
}

export interface ReporteVentas { catalogo: string; pedidos: number; montoTotal: number; }

/** Cliente HTTP de la API de GalaxyShop. */
@Injectable({ providedIn: 'root' })
export class ApiService {

  private http = inject(HttpClient);
  private base = environment.apiUrl;

  // --- publico (sin token) ---
  catalogosPublicos(): Observable<Catalogo[]> {
    return this.http.get<Catalogo[]>(`${this.base}/api/public/catalogs`);
  }
  productosPublicos(): Observable<Producto[]> {
    return this.http.get<Producto[]>(`${this.base}/api/public/products`);
  }

  // --- privado ---
  perfil(): Observable<Perfil> {
    return this.http.get<Perfil>(`${this.base}/api/me`);
  }
  productos(): Observable<Producto[]> {
    return this.http.get<Producto[]>(`${this.base}/api/products`);
  }
  misPedidos(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.base}/api/orders/me`);
  }
  crearPedido(productoId: string, cantidad: number): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.base}/api/orders`, { productoId, cantidad });
  }
  anularPedido(id: string): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.base}/api/orders/${id}/cancel`, {});
  }

  // --- especial ---
  reporteVentas(): Observable<ReporteVentas[]> {
    return this.http.get<ReporteVentas[]>(`${this.base}/api/reports/sales`);
  }
  todosLosPedidos(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.base}/api/admin/orders`);
  }
  reglasDeSeguridad(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/admin/security-rules`);
  }
  crearProducto(p: Partial<Producto>): Observable<Producto> {
    return this.http.post<Producto>(`${this.base}/api/products`, p);
  }
  eliminarProducto(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/products/${id}`);
  }
}
