package dev.beautifulbublik.monitoringsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI priceMonitorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Price Monitor API")
                        .version("v1")
                        .description("""
                                Price monitoring for products in online shops.

                                **How to use:**
                                1. `POST /api/auth/register` — obtain a JWT.
                                2. Click **Authorize** and paste the token.
                                3. `POST /api/products` — add a product by URL.
                                4. `GET /api/products/{id}/history` — data for the chart.

                                Prices are refreshed by a scheduled background job
                                (`price-monitor.scheduler.cron`). When a price drops below
                                the configured threshold, a notification is sent to email and/or Telegram.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste only the token itself, without the Bearer prefix.")));
    }
}
