package dev.beautifulbublik.monitoringsystem.controller;

import dev.beautifulbublik.monitoringsystem.dto.CreateProductRequest;
import dev.beautifulbublik.monitoringsystem.dto.ErrorResponse;
import dev.beautifulbublik.monitoringsystem.dto.PricePointResponse;
import dev.beautifulbublik.monitoringsystem.dto.ProductDetailResponse;
import dev.beautifulbublik.monitoringsystem.dto.ProductResponse;
import dev.beautifulbublik.monitoringsystem.dto.UpdateProductRequest;
import dev.beautifulbublik.monitoringsystem.security.AuthenticatedUser;
import dev.beautifulbublik.monitoringsystem.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Products", description = "Tracked products and their price history")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(
            summary = "Add a product by URL",
            description = """
                    The service parses the page immediately and stores the name, price and currency
                    as the first history point. If the price could not be obtained (the shop is not
                    supported, is unreachable or has anti-bot protection enabled), the product is not
                    created and 422 is returned — so as not to create a knowingly broken tracker.
                    """)
    @ApiResponse(responseCode = "201", description = "Product added, first price stored")
    @ApiResponse(responseCode = "409", description = "This URL is already being tracked",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Failed to obtain the price from the page",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse created = productService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List your products",
            description = "Returns only the current user's products.")
    public List<ProductResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return productService.findAll(user.getUserId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product card with full price history")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ProductDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable Long id) {
        return productService.findById(user.getUserId(), id);
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "Price history for a period",
            description = """
                    Points are sorted by ascending time — they can be fed straight into a chart.
                    The bounds are optional: without them the entire history is returned.
                    The format is ISO-8601, e.g. `2026-07-01T00:00:00Z`.
                    """)
    public List<PricePointResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,

            @Parameter(description = "Start of the period, ISO-8601", example = "2026-07-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @Parameter(description = "End of the period, ISO-8601", example = "2026-07-13T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return productService.findHistory(user.getUserId(), id, from, to);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Change tracking: pause, resume, change the threshold",
            description = "Pausing is `{\"trackingStatus\": \"PAUSED\"}`: the product and its history "
                    + "are kept, but the scheduler no longer polls it.")
    public ProductResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(user.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product along with its price history")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        productService.delete(user.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
