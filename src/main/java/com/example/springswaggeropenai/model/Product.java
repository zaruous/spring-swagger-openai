package com.example.springswaggeropenai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Represents a product in the store")
public class Product {

    @Schema(description = "Unique identifier of the product", example = "1")
    private long id;

    @Schema(description = "Name of the product", example = "Laptop")
    private String name;

    @Schema(description = "Detailed description of the product", example = "A powerful laptop for professionals")
    private String description;

    @Schema(description = "Price of the product", example = "1500.00")
    private double price;

}
