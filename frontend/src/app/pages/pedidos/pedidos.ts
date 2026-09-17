import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService, Pedido } from '../../core/api.service';
import { KeycloakService } from '../../core/keycloak.service';

/** NIVEL PRIVADO: exige sesion (authGuard). */
@Component({
  selector: 'app-pedidos',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './pedidos.html'
})
export class Pedidos implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  readonly pedidos = signal<Pedido[]>([]);
  readonly cargando = signal(true);
  readonly mensaje = signal<{ tipo: string, texto: string } | null>(null);

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.cargando.set(true);
    this.api.misPedidos().subscribe({
      next: p => { this.pedidos.set(p); this.cargando.set(false); },
      error: () => { this.mensaje.set({ tipo: 'error', texto: 'No se pudieron cargar los pedidos' }); this.cargando.set(false); }
    });
  }

  /** El boton solo se dibuja si Keycloak concedio el scope order:cancel. */
  puedeAnular(): boolean {
    return this.keycloak.puede('Pedidos', 'order:cancel');
  }

  anular(p: Pedido): void {
    this.api.anularPedido(p.id).subscribe({
      next: () => { this.mensaje.set({ tipo: 'exito', texto: 'Pedido anulado' }); this.cargar(); },
      error: err => this.mensaje.set({
        tipo: 'error',
        texto: err.status === 403
          ? 'Keycloak denego la anulacion: exige pertenecer a Gerencia y estar en horario laboral'
          : (err.error?.mensaje ?? 'No se pudo anular')
      })
    });
  }
}
