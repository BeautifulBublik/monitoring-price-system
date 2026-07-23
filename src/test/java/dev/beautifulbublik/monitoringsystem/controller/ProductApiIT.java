package dev.beautifulbublik.monitoringsystem.controller;

import dev.beautifulbublik.monitoringsystem.parser.ParsedPrice;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingException;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the REST layer on a live Spring context and H2.
 * <p>
 * We do not go out to the network: {@link PriceParsingService} is replaced with a mock — the test
 * verifies the API contract and data isolation, not the availability of third-party sites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductApiIT {

    private static final String URL = "https://example-shop.com/product/42";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceParsingService priceParsingService;

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "P@ssw0rd123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return "Bearer " + token;
    }

    private void givenParsedPrice(String price) {
        when(priceParsingService.parse(anyString()))
                .thenReturn(new ParsedPrice("Example Pro 14 Laptop", new BigDecimal(price), "UAH"));
    }

    @Test
    @DisplayName("Full scenario: register -> add product -> list -> history -> delete")
    void fullProductLifecycle() throws Exception {
        String auth = registerAndGetToken();
        givenParsedPrice("89990.00");

        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "thresholdType": "PERCENT",
                                 "thresholdBase": "LAST_PRICE", "thresholdValue": 10.00}
                                """.formatted(URL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Example Pro 14 Laptop")))
                .andExpect(jsonPath("$.shopName", is("example-shop.com")))
                .andExpect(jsonPath("$.currentPrice", is(89990.00)))
                .andExpect(jsonPath("$.currency", is("UAH")))
                .andExpect(jsonPath("$.trackingStatus", is("ACTIVE")))
                .andReturn();

        String productId = created.getResponse().getContentAsString()
                .replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");

        mockMvc.perform(get("/api/products").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url", is(URL)));

        mockMvc.perform(get("/api/products/" + productId + "/history").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].price", is(89990.00)))
                .andExpect(jsonPath("$[0].currency", is("UAH")));

        mockMvc.perform(get("/api/products/" + productId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id", is(Integer.parseInt(productId))))
                .andExpect(jsonPath("$.minPrice", is(89990.00)))
                .andExpect(jsonPath("$.history", hasSize(1)));

        mockMvc.perform(delete("/api/products/" + productId).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Products are inaccessible without a token — 401")
    void rejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("One user's products are not visible to another")
    void isolatesProductsBetweenUsers() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        givenParsedPrice("1000.00");

        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}
                                """.formatted(URL)))
                .andExpect(status().isCreated())
                .andReturn();

        String productId = created.getResponse().getContentAsString()
                .replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");

        mockMvc.perform(get("/api/products").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/products/" + productId).header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An invalid URL is rejected by validation with a field description")
    void validatesUrl() throws Exception {
        String auth = registerAndGetToken();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    @DisplayName("Shop unreachable — 422, product not created")
    void returns422WhenParsingFails() throws Exception {
        String auth = registerAndGetToken();
        when(priceParsingService.parse(anyString()))
                .thenThrow(new PriceParsingException("Site unreachable"));

        mockMvc.perform(post("/api/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}
                                """.formatted(URL)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status", is(422)));

        mockMvc.perform(get("/api/products").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Duplicate email on registration — 409")
    void rejectsDuplicateEmail() throws Exception {
        String body = """
                {"email": "duplicate@example.com", "password": "P@ssw0rd123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Notification settings: email is on by default, Telegram cannot be enabled without a chat ID")
    void notificationSettingsFlow() throws Exception {
        String auth = registerAndGetToken();

        mockMvc.perform(get("/api/settings/notifications").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled", is(true)))
                .andExpect(jsonPath("$.telegramEnabled", is(false)));

        mockMvc.perform(put("/api/settings/notifications")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailEnabled": true, "telegramEnabled": true}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/settings/notifications")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailEnabled": false, "telegramEnabled": true, "telegramChatId": "123456789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled", is(false)))
                .andExpect(jsonPath("$.telegramEnabled", is(true)))
                .andExpect(jsonPath("$.telegramChatId", is("123456789")));
    }
}
