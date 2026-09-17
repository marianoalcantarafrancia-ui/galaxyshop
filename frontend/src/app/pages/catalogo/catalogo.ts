import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ApiService, Producto } from '../../core/api.service';
import { KeycloakService } from '../../core/keycloak.service';

/**
 * Pagina mixta: el listado es publico, pero comprar o administrar productos
 * aparece solo si el token lo permite.
 */
@Component({
  selector: 'app-catalogo',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './catalogo.html'
})
export class Catalogo implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  readonly productos = signal<Producto[]>([]);
  readonly cargando = signal(true);
  readonly mensaje = signal<{ tipo: string, texto: string } | null>(null);
  readonly cantidades: Record<string, number> = {};

  ngOnInit(): void { this.cargar(); }

  private cargar(): void {
    this.cargando.set(true);
    const peticion = this.keycloak.autenticado()
      ? this.api.productos()          // privada: exige catalog-read
      : this.api.productosPublicos(); // publica: escaparate

    peticion.subscribe({
      next: p => {
        this.productos.set(p);
        p.forEach(x => this.cantidades[x.id] ??= 1);
        this.cargando.set(false);
      },
      error: () => {
        this.mensaje.set({ tipo: 'error', texto: 'No se pudo cargar el catalogo' });
        this.cargando.set(false);
      }
    });
  }

  /** Solo tiene sentido si Keycloak concedio el scope order:create. */
  puedeComprar(): boolean {
    return this.keycloak.puede('Pedidos', 'order:create');
  }

  puedeEliminar(): boolean {
    return this.keycloak.puede('Catalogo', 'catalog:delete');
  }

  comprar(p: Producto): void {
    this.api.crearPedido(p.id, this.cantidades[p.id] ?? 1).subscribe({
      next: pedido => {
        this.mensaje.set({ tipo: 'exito', texto: `Pedido registrado: ${pedido.cantidad} x ${pedido.producto}` });
        this.cargar();
      },
      error: err => this.mensaje.set({ tipo: 'error', texto: this.textoDeError(err) })
    });
  }

  eliminar(p: Producto): void {
    this.api.eliminarProducto(p.id).subscribe({
      next: () => { this.mensaje.set({ tipo: 'exito', texto: `Producto "${p.nombre}" eliminado` }); this.cargar(); },
      error: err => this.mensaje.set({ tipo: 'error', texto: this.textoDeError(err) })
    });
  }

  private textoDeError(err: any): string {
    if (err.status === 401) return 'Necesitas iniciar sesion';
    if (err.status === 403) return err.error?.mensaje ?? 'Keycloak denego esta operacion';
    return err.error?.mensaje ?? 'Ocurrio un error inesperado';
  }
}
