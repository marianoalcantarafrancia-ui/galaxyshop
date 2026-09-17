package com.galaxyshop.api.config;

import com.galaxyshop.api.model.Product;
import com.galaxyshop.api.model.ProductCatalog;
import com.galaxyshop.api.model.SecurityRule;
import com.galaxyshop.api.repository.ProductCatalogRepository;
import com.galaxyshop.api.repository.ProductRepository;
import com.galaxyshop.api.repository.SecurityRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Carga el catalogo de demostracion y las reglas de seguridad administrables.
 *
 * Los productos provienen del proyecto de ejemplo publico
 * https://github.com/jannie-louwrens/spring-boot-keycloak-angular
 */
@Configuration
public class CargaInicialDeDatos {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialDeDatos.class);

    @Bean
    CommandLineRunner cargarCatalogo(ProductCatalogRepository catalogos, ProductRepository productos) {
        return args -> {
            if (catalogos.count() > 0) {
                log.info("El catalogo ya tiene datos; no se vuelve a cargar");
                return;
            }

            ProductCatalog panaderia = catalogos.save(new ProductCatalog("Panaderia",
                    "Pan, bolleria y pasteleria horneados cada manana"));
            ProductCatalog lacteos = catalogos.save(new ProductCatalog("Lacteos",
                    "Leche, quesos, yogures y mantequilla"));
            ProductCatalog carniceria = catalogos.save(new ProductCatalog("Carniceria",
                    "Cortes de res, cerdo, pollo y cordero"));
            ProductCatalog frutasVerduras = catalogos.save(new ProductCatalog("Frutas y Verduras",
                    "Produccion fresca de temporada"));
            ProductCatalog hogar = catalogos.save(new ProductCatalog("Hogar",
                    "Limpieza y articulos para el hogar"));

            productos.saveAll(List.of(
                    new Product("Pan ciabatta", "Panecillos de miga ligera y corteza crujiente",
                            new BigDecimal("4.25"), 120, panaderia),
                    new Product("Pastel de calabaza", "Relleno especiado de calabaza sobre masa quebrada",
                            new BigDecimal("45.99"), 18, panaderia),
                    new Product("Leche entera", "Leche fresca pasteurizada, botella de 1 litro",
                            new BigDecimal("24.50"), 200, lacteos),
                    new Product("Queso fresco", "Queso fresco artesanal, 400 gramos",
                            new BigDecimal("32.00"), 60, lacteos),
                    new Product("Carne molida", "Res molida sin conservantes, bandeja de 500 gramos",
                            new BigDecimal("100.50"), 45, carniceria),
                    new Product("Chuletas de cordero", "Chuletas de lomo, listas para la parrilla",
                            new BigDecimal("76.50"), 30, carniceria),
                    new Product("Brocoli", "Ramilletes frescos, ricos en fibra",
                            new BigDecimal("11.25"), 90, frutasVerduras),
                    new Product("Sandia", "Sandia de temporada, dulce y jugosa",
                            new BigDecimal("89.90"), 25, frutasVerduras),
                    new Product("Papa blanca", "Papa harinosa, ideal para hornear o freir",
                            new BigDecimal("29.99"), 150, frutasVerduras),
                    new Product("Detergente liquido", "Detergente concentrado, 3 litros",
                            new BigDecimal("38.90"), 70, hogar)));

            log.info("Catalogo de demostracion cargado: {} catalogos, {} productos",
                    catalogos.count(), productos.count());
        };
    }

    /**
     * Rubrica #4 - tercera via de personalizacion: BASE DE DATOS.
     * Estas filas las lee SecurityConfig al construir el SecurityFilterChain.
     */
    @Bean
    CommandLineRunner cargarReglasDeSeguridad(SecurityRuleRepository reglas) {
        return args -> {
            if (reglas.count() > 0) {
                return;
            }
            reglas.saveAll(List.of(
                    new SecurityRule("/api/public/**", "*", "", true, 10,
                            "Escaparate publico de la tienda"),
                    new SecurityRule("/swagger-ui/**", "*", "", true, 11,
                            "Documentacion interactiva de la API"),
                    new SecurityRule("/v3/api-docs/**", "*", "", true, 12,
                            "Especificacion OpenAPI"),
                    new SecurityRule("/actuator/health", "*", "", true, 13,
                            "Sonda de salud para el orquestador"),
                    new SecurityRule("/api/me/**", "*", "", false, 20,
                            "Perfil del usuario: basta con estar autenticado"),
                    new SecurityRule("/api/reports/**", "GET",
                            "report-read,gs-auditor,gs-manager,gs-admin", false, 30,
                            "Reportes de venta: acceso especial por rol")));
            log.info("Reglas de seguridad administrables cargadas: {}", reglas.count());
        };
    }
}
