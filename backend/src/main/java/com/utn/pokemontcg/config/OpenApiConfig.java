package com.utn.pokemontcg.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pokemonTcgOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Pokémon TCG API")
                .description("API del TPI de Programación III — implementación del TCG (set XY1).")
                .version("v0.1.0")
                .contact(new Contact().name("Grupo TPI Pokémon TCG"))
                .license(new License().name("Academic use").url("https://www.utn.edu.ar/")));
    }
}
