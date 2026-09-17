import { Component, inject, signal, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiService, ReporteVentas } from '../../core/api.service';
import { KeycloakService } from '../../core/keycloak.service';

/** NIVEL ESPECIAL: exige rol de auditoria, gerencia o administracion. */
@Component({
  selector: 'app-reportes',
  imports: [DecimalPipe],
  templateUrl: './reportes.html'
})
export class Reportes implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  readonly filas = signal<ReporteVentas[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.reporteVentas().subscribe({
      next: r => { this.filas.set(r); this.cargando.set(false); },
      error: err => {
        this.error.set(err.status === 403
          ? 'Keycloak denego el acceso al reporte: exige pertenecer a Gerencia o Auditoria'
          : 'No se pudo cargar el reporte');
        this.cargando.set(false);
      }
    });
  }

  total(): number {
    return this.filas().reduce((s, f) => s + f.montoTotal, 0);
  }

  totalPedidos(): number {
    return this.filas().reduce((s, f) => s + f.pedidos, 0);
  }
}
