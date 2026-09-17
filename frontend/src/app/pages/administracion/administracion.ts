import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService, Pedido } from '../../core/api.service';
import { KeycloakService } from '../../core/keycloak.service';

/** NIVEL ESPECIAL: reservado al rol de realm gs-admin. */
@Component({
  selector: 'app-administracion',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './administracion.html'
})
export class Administracion implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  readonly pedidos = signal<Pedido[]>([]);
  readonly reglas = signal<any[]>([]);
  readonly cargando = signal(true);

  ngOnInit(): void {
    this.api.todosLosPedidos().subscribe({
      next: p => { this.pedidos.set(p); this.cargando.set(false); },
      error: () => this.cargando.set(false)
    });
    this.api.reglasDeSeguridad().subscribe({ next: r => this.reglas.set(r) });
  }
}
