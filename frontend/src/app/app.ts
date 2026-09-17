import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { KeycloakService } from './core/keycloak.service';
import { ApiService } from './core/api.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {

  readonly keycloak = inject(KeycloakService);
  private api = inject(ApiService);

  ngOnInit(): void {
    // Con sesion iniciada se pide el perfil efectivo: roles, grupos y
    // permisos granulares que el menu usa para mostrarse u ocultarse.
    if (this.keycloak.autenticado()) {
      this.api.perfil().subscribe({
        next: p => this.keycloak.perfil.set(p),
        error: err => console.error('No se pudo cargar el perfil', err)
      });
    }
  }
}
