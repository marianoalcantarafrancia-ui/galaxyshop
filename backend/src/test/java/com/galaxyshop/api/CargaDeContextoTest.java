package com.galaxyshop.api;

import com.galaxyshop.api.repository.ProductCatalogRepository;
import com.galaxyshop.api.repository.SecurityRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que la aplicacion arranca y que la carga inicial hizo su trabajo. */
@SpringBootTest
@ActiveProfiles("test")
class CargaDeContextoTest {

    @Autowired ProductCatalogRepository catalogos;
    @Autowired SecurityRuleRepository reglas;

    @Test
    @DisplayName("El catalogo de demostracion se carga al arrancar")
    void catalogoInicialCargado() {
        assertThat(catalogos.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Las reglas de seguridad administrables estan en base de datos")
    void reglasDeSeguridadCargadas() {
        var activas = reglas.findByEnabledTrueOrderBySortOrderAsc();
        assertThat(activas).isNotEmpty();
        assertThat(activas).anyMatch(r -> r.getPattern().equals("/api/public/**") && r.isPublicAccess());
        assertThat(activas).anyMatch(r -> r.getPattern().equals("/api/reports/**") && !r.isPublicAccess());
    }
}
