/** Configuracion del entorno productivo. */
export const environment = {
  production: true,
  apiUrl: '/api-gateway',
  keycloak: {
    url: 'https://sso.galaxyshop.test',
    realm: 'galaxyshop',
    clientId: 'shop-frontend',
    // Cliente cuyos roles viajan en resource_access del token
    resourceClientId: 'shop-backend'
  }
};
