package com.galaxyshop.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API REST de GalaxyShop.
 *
 * El caso de negocio (catalogos, productos y pedidos de una tienda de
 * comestibles) esta tomado del proyecto de ejemplo publico
 * https://github.com/jannie-louwrens/spring-boot-keycloak-angular
 * y actualizado a Spring Boot 3 + Keycloak 26.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GalaxyShopApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GalaxyShopApiApplication.class, args);
    }
}
