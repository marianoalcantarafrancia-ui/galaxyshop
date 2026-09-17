import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, Catalogo } from '../../core/api.service';
import { KeycloakService } from '../../core/keycloak.service';

/** NIVEL PUBLICO: se ve sin iniciar sesion. */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink],
  templateUrl: './inicio.html'
})
export class Inicio implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  readonly catalogos = signal<Catalogo[]>([]);
  readonly cargando = signal(true);

  ngOnInit(): void {
    this.api.catalogosPublicos().subscribe({
      next: c => { this.catalogos.set(c); this.cargando.set(false); },
      error: () => this.cargando.set(false)
    });
  }
}
