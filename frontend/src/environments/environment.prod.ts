/** Configuracion del entorno productivo. */
export const environment = {
  production: true,
  apiUrl: '/api-gateway',
  keycloak: {
    url: 'https://sso.galaxyshop.test',
    realm: 'galaxyshop',
    clientId: 'shop-frontend'
  }
};
