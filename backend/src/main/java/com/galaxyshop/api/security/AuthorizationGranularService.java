package com.galaxyshop.api.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rubrica #6 - Seguridad granular.
 *
 * Pregunta a Keycloak si el portador del token puede ejercer un scope sobre un
 * recurso concreto. La decision NO esta en este codigo: se resuelve evaluando
 * las Policies y Permissions configuradas en el cliente 'shop-backend'.
 *
 * Se usa el grant UMA 2.0:
 *   POST /realms/{realm}/protocol/openid-connect/token
 *        grant_type=urn:ietf:params:oauth:grant-type:uma-ticket
 *        audience=shop-backend
 *        permission=Pedidos#order:cancel
 *        response_mode=decision
 *
 * Ventaja practica: para cambiar quien puede anular un pedido basta editar la
 * policy en la consola de Keycloak; la aplicacion no se toca ni se reinicia.
 */
@Service
public class AuthorizationGranularService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationGranularService.class);
    private static final String UMA_GRANT = "urn:ietf:params:oauth:grant-type:uma-ticket";

    private final RestClient http = RestClient.create();
    // Spring Boot 4 trae Jackson 3: JsonMapper sustituye a ObjectMapper
    private final JsonMapper json = JsonMapper.builder().build();
    private final UsuarioActual usuarioActual;
    private final AppSecurityProperties propiedades;
    private final String tokenEndpoint;

    /** Cache corta para no consultar a Keycloak en cada llamada del mismo usuario. */
    private final Map<String, Decision> cache = new ConcurrentHashMap<>();

    private record Decision(boolean permitido, Instant vence) { }

    public AuthorizationGranularService(UsuarioActual usuarioActual,
                                        AppSecurityProperties propiedades,
                                        org.springframework.core.env.Environment env) {
        this.usuarioActual = usuarioActual;
        this.propiedades = propiedades;
        String issuer = env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        this.tokenEndpoint = issuer + "/protocol/openid-connect/token";
    }

    /**
     * ¿Puede el usuario actual ejercer 'scope' sobre 'recurso'?
     * Pensado para usarse dentro de @PreAuthorize.
     */
    public boolean permite(String recurso, String scope) {
        if (!propiedades.getGranular().isEnabled()) {
            return true;
        }
        String accessToken = usuarioActual.token().map(t -> t.getTokenValue()).orElse(null);
        if (accessToken == null) {
            return false;
        }

        String permiso = recurso + "#" + scope;
        String clave = usuarioActual.username() + "|" + permiso;

        Decision enCache = cache.get(clave);
        if (enCache != null && enCache.vence().isAfter(Instant.now())) {
            return enCache.permitido();
        }

        boolean permitido = consultarKeycloak(accessToken, permiso);
        cache.put(clave, new Decision(permitido,
                Instant.now().plus(Duration.ofSeconds(propiedades.getGranular().getCacheSeconds()))));

        log.debug("Decision granular [{}] {} -> {}", usuarioActual.username(), permiso,
                permitido ? "PERMITIDO" : "DENEGADO");
        return permitido;
    }

    private boolean consultarKeycloak(String accessToken, String permiso) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", UMA_GRANT);
        form.add("audience", propiedades.getGranular().getAudience());
        form.add("permission", permiso);
        form.add("response_mode", "decision");

        try {
            String cuerpo = http.post()
                    .uri(tokenEndpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode nodo = json.readTree(cuerpo == null ? "{}" : cuerpo);
            return nodo.path("result").asBoolean(false);

        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.BadRequest e) {
            // Keycloak responde 403/400 con access_denied cuando ninguna policy concede el permiso
            return false;
        } catch (Exception e) {
            log.error("Error consultando la autorizacion granular de '{}': {}", permiso, e.getMessage());
            return false;
        }
    }

    /**
     * Devuelve todos los permisos concedidos al usuario (RPT completo).
     * Lo consume el frontend para mostrar u ocultar botones.
     */
    public List<Map<String, Object>> permisosDelUsuario() {
        String accessToken = usuarioActual.token().map(t -> t.getTokenValue()).orElse(null);
        if (accessToken == null || !propiedades.getGranular().isEnabled()) {
            return List.of();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", UMA_GRANT);
        form.add("audience", propiedades.getGranular().getAudience());
        form.add("response_mode", "permissions");

        try {
            String cuerpo = http.post()
                    .uri(tokenEndpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            return json.readValue(cuerpo == null ? "[]" : cuerpo,
                    json.getTypeFactory().constructCollectionType(List.class, Map.class));

        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.BadRequest e) {
            return List.of();
        } catch (Exception e) {
            log.error("Error obteniendo los permisos del usuario: {}", e.getMessage());
            return List.of();
        }
    }
}
