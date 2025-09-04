package com.example.springswaggeropenai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc; // For getting the OpenAPI spec

    @Autowired
    private ObjectMapper objectMapper;

    // --- Standard API Functional Tests (Restored) ---

    @Test
    void testGetAllProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name", is("Laptop")));
    }

    @Test
    void testGetProductById_Found() throws Exception {
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Laptop")));
    }

    @Test
    void testGetProductById_NotFound() throws Exception {
        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DirtiesContext
    void testCreateProduct() throws Exception {
        com.example.springswaggeropenai.model.Product newProduct = new com.example.springswaggeropenai.model.Product(0, "New Keyboard", "Mechanical gaming keyboard", 150.0);
        String productJson = objectMapper.writeValueAsString(newProduct);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(4)))
                .andExpect(jsonPath("$.name", is("New Keyboard")));
    }

    // --- Utility and Client Classes for E2E Test ---

    static class OpenApiToGeminiConverter {
        private final ObjectMapper mapper = new ObjectMapper();
        public String convert(String openApiSpecJson) throws Exception {
            JsonNode openApiSpec = mapper.readTree(openApiSpecJson);
            ArrayNode allFunctionTools = mapper.createArrayNode(); // This will hold multiple tool objects

            Iterator<Map.Entry<String, JsonNode>> paths = openApiSpec.get("paths").fields();
            while (paths.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = paths.next();
                Iterator<Map.Entry<String, JsonNode>> methods = pathEntry.getValue().fields();

                while (methods.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methods.next();
                    JsonNode endpoint = methodEntry.getValue();

                    if (!endpoint.has("operationId")) continue; // Skip if no operationId
                    
                    ObjectNode singleToolWrapper = allFunctionTools.addObject(); // New wrapper for each function
                    int index = allFunctionTools.size() - 1;
                    ArrayNode functionDeclarations = singleToolWrapper.putArray("functionDeclarations");

                    ObjectNode geminiFunction = functionDeclarations.addObject();
                    geminiFunction.put("name", endpoint.get("operationId").asText());
                    geminiFunction.put("description", endpoint.get("summary").asText());

                    ObjectNode params = geminiFunction.putObject("parameters");
                    params.put("type", "OBJECT");
                    ObjectNode props = params.putObject("properties");
                    ArrayNode required = params.putArray("required");
                    
                    if (endpoint.has("parameters")) {
                        for (JsonNode param : endpoint.get("parameters")) {
                        	if(param.get("description") == null)
                    		{
                        		allFunctionTools.remove(index);
                        		break;
                    		}
                        	
                            String paramName = param.get("name").asText();
                            String paramDescription = param.get("description") == null ? "" : param.get("description").asText();
                            String openApiType = param.at("/schema/type").asText();

                            ObjectNode prop = props.putObject(paramName);
                            prop.put("type", mapOpenApiTypeToGemini(openApiType));
                            prop.put("description", paramDescription);

                            if (param.get("required").asBoolean(false)) {
                                required.add(paramName);
                            }
                        }
                    }
                    else
                    {
                    	allFunctionTools.remove(index);
                		break;
                    }
                }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(allFunctionTools);
        }

        private String mapOpenApiTypeToGemini(String openApiType) {
            return switch (openApiType) {
                case "string" -> "STRING";
                case "number", "integer" -> "NUMBER";
                case "boolean" -> "BOOLEAN";
                default -> "STRING";
            };
        }
    }

    static class GeminiFunctionCallingClient {
        private final String apiKey;
        public GeminiFunctionCallingClient(String apiKey) { this.apiKey = apiKey; }
        public JsonNode getFunctionCall(String naturalLanguageQuery, String geminiToolsJson) throws Exception {
            if (apiKey == null || apiKey.equals("YOUR_API_KEY") || apiKey.isBlank()) {
                throw new IllegalArgumentException("API Key is not set.");
            }
            // Correctly escaped JSON format string
            String requestBody = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}],\"tools\":%s}", naturalLanguageQuery.replace("\"", "\\\""), geminiToolsJson);
            System.err.println(requestBody);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to call Gemini API: " + response.body());
            }
            ObjectMapper objectMapper = new ObjectMapper(); 
            JsonNode root = objectMapper.readTree(response.body());
            return root.at("/candidates/0/content/parts/0/functionCall");
        }
    }

    /**
     * A simple HTTP client to call our own running Spring Boot application.
     */
    static class ProductApiClient {
        private final HttpClient client = HttpClient.newHttpClient();
        private final String baseUrl = "http://localhost:8080";

        public String searchProducts(String name) throws Exception {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/products/search?name=" + encodedName))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }

    // --- Final E2E Test for Dynamic Function Calling ---

    @Test
//    @Disabled("This is the final E2E test. Run the app first, then run this test.")
    void testDynamicFunctionCalling_E2E() throws Exception {
        // 1. Get the OpenAPI specification from our running application
        String openApiSpecJson = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        // 2. Dynamically convert the OpenAPI spec to Gemini's Function Declaration format
        OpenApiToGeminiConverter converter = new OpenApiToGeminiConverter();
        String geminiToolsJson = converter.convert(openApiSpecJson);
        System.out.println("Dynamically Generated Gemini Tools Schema:\n" + geminiToolsJson);

        // 3. IMPORTANT: Replace "YOUR_API_KEY" with your actual Gemini API key.
        String apiKey = "AIzaSyDMFeQWLB30GanVYK9z92aPWJ9ySrIBunU";
        GeminiFunctionCallingClient geminiClient = new GeminiFunctionCallingClient(apiKey);

        // 4. Define a natural language query
        String query = "Find the product named 'Smartphone'";

        // 5. Get the structured FunctionCall object from Gemini
        JsonNode functionCall = geminiClient.getFunctionCall(query, geminiToolsJson);
        System.out.println("\nGemini's Function Call suggestion: " + functionCall.toString());

        // 6. Verify the function name and arguments
        String functionName = functionCall.get("name").asText();
        JsonNode functionArgs = functionCall.get("args");

        assertThat(functionName, is("searchProducts"));
        assertThat(functionArgs.get("name").asText(), is("Smartphone"));

        // 7. Execute the function call and verify the result
        if ("searchProducts".equals(functionName)) {
            String name = functionArgs.get("name").asText();
            String responseJson = new ProductApiClient().searchProducts(name);
            
            // 7. Verify the result from the real API call
            JsonNode responseNode = objectMapper.readTree(responseJson);
            assertThat(responseNode.get(0).get("id").asInt(), is(2));
            System.out.println("\nSuccessfully executed the function call dynamically generated by Gemini and verified the result.");
        }
    }
}