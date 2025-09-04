package com.example.springswaggeropenai.controller;

import com.example.springswaggeropenai.model.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.websocket.server.PathParam;

import org.springframework.context.annotation.Description;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Management", description = "APIs for managing products")
public class ProductController {

    private final Map<Long, Product> productStore = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public ProductController() {
        // Initialize with some sample data
        productStore.put(counter.incrementAndGet(), new Product(1L, "Laptop", "High-performance gaming laptop", 2500.0));
        productStore.put(counter.incrementAndGet(), new Product(2L, "Smartphone", "Latest model with AI camera", 1200.0));
        productStore.put(counter.incrementAndGet(), new Product(3L, "Headphones", "Noise-cancelling wireless headphones", 350.0));
    }

    @Operation(summary = "Get all products", description = "Retrieve a list of all products.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    @GetMapping
    public List<Product> getAllProducts() {
        return new ArrayList<>(productStore.values());
    }

    @Operation(summary = "Get a product by ID", description = "Retrieve a single product by its unique ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found the product"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(
            @Parameter(description = "ID of the product to be obtained. Cannot be empty.", required = true) @PathVariable long id) {
        Product product = productStore.get(id); 
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Create a new product", description = "Add a new product to the store.")
    @ApiResponse(responseCode = "201", description = "Product created successfully", content = @Content(schema = @Schema(implementation = Product.class)))
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        long newId = counter.incrementAndGet();
        product.setId(newId);
        productStore.put(newId, product);
        return new ResponseEntity<>(product, HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing product", description = "Update the details of an existing product by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable long id, @RequestBody Product product) {
        if (!productStore.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }
        product.setId(id);
        productStore.put(id, product);
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Delete a product", description = "Delete a product from the store by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable long id) {
        if (productStore.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search for products by name", description = "Find products containing the given name string.")
    @GetMapping("/search")
    public List<Product> searchProducts(
    		 @Parameter(description = "Product Name", required = true, example = "Smartphone")
    		String name) {
        return productStore.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get product categories", description = "Retrieve a list of unique product categories.")
    @GetMapping("/categories")
    public List<String> getProductCategories() {
        // In a real app, this would come from a Category entity
        return List.of("Electronics", "Gaming", "Accessories");
    }

    @Operation(summary = "Update a product's price", description = "Partially update only the price of a product.")
    @PatchMapping("/{id}/price")
    public ResponseEntity<Product> updateProductPrice(@PathVariable long id, @RequestBody Map<String, Double> priceUpdate) {
        Product product = productStore.get(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        product.setPrice(priceUpdate.get("price"));
        productStore.put(id, product);
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Get total number of products", description = "Returns the total count of products in the store.")
    @GetMapping("/count")
    public long getProductCount() {
        return productStore.size();
    }

    @Operation(summary = "Check service health", description = "A simple endpoint to check if the service is running.")
    @GetMapping("/health")
    public String healthCheck() {
        return "Product Service is UP and RUNNING";
    }
}
