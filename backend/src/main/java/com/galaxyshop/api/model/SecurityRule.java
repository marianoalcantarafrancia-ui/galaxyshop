package com.galaxyshop.api.model;

import jakarta.persistence.*;

/**
 * Regla de acceso almacenada en base de datos.
 *
 * Rubrica #4: los valores de la configuracion de seguridad deben poder
 * personalizarse "a nivel de constantes, archivos properties o base de datos".
 * Esta tabla cubre la tercera via: el administrador cambia una fila y, al
 * reiniciar, el SecurityFilterChain publica la nueva regla sin recompilar.
 */
@Entity
@Table(name = "security_rule")
public class SecurityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Patron de ruta estilo Ant, por ejemplo /api/reports/**. */
    @Column(nullable = false, length = 200)
    private String pattern;

    /** Metodo HTTP o '*' para todos. */
    @Column(nullable = false, length = 10)
    private String method = "*";

    /** Roles aceptados, separados por coma. Vacio = basta con estar autenticado. */
    @Column(name = "required_roles", length = 400)
    private String requiredRoles = "";

    /** Si es true la ruta es publica y no exige token. */
    @Column(name = "public_access", nullable = false)
    private boolean publicAccess = false;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Orden de evaluacion: el filtro aplica primero los valores mas bajos. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(length = 300)
    private String description;

    protected SecurityRule() { }

    public SecurityRule(String pattern, String method, String requiredRoles,
                        boolean publicAccess, int sortOrder, String description) {
        this.pattern = pattern;
        this.method = method;
        this.requiredRoles = requiredRoles == null ? "" : requiredRoles;
        this.publicAccess = publicAccess;
        this.sortOrder = sortOrder;
        this.description = description;
    }

    public String[] rolesArray() {
        if (requiredRoles == null || requiredRoles.isBlank()) {
            return new String[0];
        }
        return requiredRoles.split("\\s*,\\s*");
    }

    public Long getId() { return id; }
    public String getPattern() { return pattern; }
    public String getMethod() { return method; }
    public String getRequiredRoles() { return requiredRoles; }
    public boolean isPublicAccess() { return publicAccess; }
    public boolean isEnabled() { return enabled; }
    public int getSortOrder() { return sortOrder; }
    public String getDescription() { return description; }
}
