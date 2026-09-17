package com.galaxyshop.api.security;

/**
 * Rubrica #4 - primera via de personalizacion: CONSTANTES.
 *
 * Los nombres de roles y scopes viven en un unico lugar del codigo. Si manana
 * el realm renombra 'gs-clerk', se corrige aqui y no en veinte anotaciones.
 */
public final class SecurityRoles {

    private SecurityRoles() { }

    // ---- Roles de realm (llegan en realm_access.roles) ----
    public static final String CUSTOMER  = "gs-customer";
    public static final String CLERK     = "gs-clerk";
    public static final String WAREHOUSE = "gs-warehouse";
    public static final String AUDITOR   = "gs-auditor";
    public static final String MANAGER   = "gs-manager";
    public static final String ADMIN     = "gs-admin";

    // ---- Roles de cliente (llegan en resource_access.shop-backend.roles) ----
    public static final String CATALOG_READ   = "catalog-read";
    public static final String CATALOG_WRITE  = "catalog-write";
    public static final String CATALOG_DELETE = "catalog-delete";
    public static final String ORDER_READ_OWN = "order-read-own";
    public static final String ORDER_READ_ALL = "order-read-all";
    public static final String ORDER_CREATE   = "order-create";
    public static final String ORDER_CANCEL   = "order-cancel";
    public static final String CUSTOMER_READ  = "customer-read";
    public static final String REPORT_READ    = "report-read";

    // ---- Scopes de Authorization Services (seguridad granular) ----
    public static final String SCOPE_CATALOG_VIEW   = "catalog:view";
    public static final String SCOPE_CATALOG_EDIT   = "catalog:edit";
    public static final String SCOPE_CATALOG_DELETE = "catalog:delete";
    public static final String SCOPE_ORDER_VIEW     = "order:view";
    public static final String SCOPE_ORDER_CREATE   = "order:create";
    public static final String SCOPE_ORDER_CANCEL   = "order:cancel";
    public static final String SCOPE_REPORT_VIEW    = "report:view";

    // ---- Recursos protegidos declarados en Keycloak ----
    public static final String RES_CATALOGO = "Catalogo";
    public static final String RES_PEDIDOS  = "Pedidos";
    public static final String RES_REPORTES = "Reportes";

    /** Spring Security compara authorities con el prefijo ROLE_ en hasRole(). */
    public static final String PREFIJO = "ROLE_";

    public static String conPrefijo(String rol) {
        return PREFIJO + rol;
    }
}
