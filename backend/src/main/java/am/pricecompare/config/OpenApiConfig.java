package am.pricecompare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Where Is It Cheapest? API").version("0.1.0")
                        .description("Real grocery prices from Armenian online supermarkets: search, cross-store comparison, "
                                + "price history, shopping lists and basket optimization. All prices are captured from public "
                                + "store catalogs; every price carries capturedAt and a freshness level."))
                .components(new Components().addSecuritySchemes("adminToken",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(AdminAuthFilter.HEADER)));
    }
}
