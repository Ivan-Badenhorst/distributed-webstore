package be.kuleuven.dsgt4;

import be.kuleuven.dsgt4.auth.WebSecurityConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.core.ApiFuture;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import java.util.*;

@RestController
class DBController {

    @Autowired
    WebClient.Builder webClientBuilder;

    @Autowired
    Firestore db;



    @PostMapping("/api/newUser")
    @ResponseBody
    public User newuser() {
        var user = WebSecurityConfig.getUser();

        Map<String, Object> data = new HashMap<>();
        data.put("user", user.getEmail());
        data.put("role", user.getRole());

        this.db.collection("user").document(user.getEmail().toString()).set(data);

        return user;
    }


    @GetMapping("/api/getBundles")
    public String getBundles() throws InterruptedException, ExecutionException {
        var user = WebSecurityConfig.getUser();
        WebClient webClient = webClientBuilder.build();
        String[] endpointURLs = {
                "http://sud.switzerlandnorth.cloudapp.azure.com:8080/products/",
                "http://ivan.canadacentral.cloudapp.azure.com:8080/products/",
                "http://sud.japaneast.cloudapp.azure.com:8080/products/"
        };

        try {
            // Reference to the bundles collection in Firestore
            CollectionReference bundlesRef = this.db.collection("bundles");

            // Query to retrieve all documents in the bundles collection
            Query query = bundlesRef;

            // Execute the query and retrieve all bundle documents
            QuerySnapshot querySnapshot = query.get().get();

            // StringBuilder to construct the JSON string
            StringBuilder jsonDataBuilder = new StringBuilder();
            jsonDataBuilder.append("{\n");
            jsonDataBuilder.append("  \"bundles\": [\n");

            // Iterate over each document in the query result
            for (QueryDocumentSnapshot document : querySnapshot) {
                // Extract data from the document
                String name = document.getString("name");
                String description = document.getString("description");
                String price = document.getString("price");

                // Append bundle details to the JSON string
                jsonDataBuilder.append("    {\n");
                jsonDataBuilder.append("      \"name\": \"").append(name).append("\",\n");
                jsonDataBuilder.append("      \"description\": \"").append(description).append("\",\n");
                jsonDataBuilder.append("      \"products\": [\n");


                // Extract product IDs from the document
                List<String> productIds = (List<String>) document.get("productIds");

                int i=0;
                for (String productId : productIds) {
                    String endpointURL=endpointURLs[i]+productId;
                    System.out.println(endpointURL);
                    i++;
                    String responseBody = webClient.get()
                            .uri(endpointURL)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();


                    try{

                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode rootNode = objectMapper.readTree(responseBody);

                        String productName = rootNode.path("name").asText();
                        //double productPrice = rootNode.path("price").asDouble();
                        String productDescription = rootNode.path("description").asText();
                        String imageLink = rootNode.path("imageLink").asText();
                        System.out.println(productName);

                        // Append product details to the JSON string
                        jsonDataBuilder.append("        {\n");
                        jsonDataBuilder.append("          \"name\": \"").append(productName).append("\",\n");
                        jsonDataBuilder.append("          \"description\": \"").append(productDescription).append("\",\n");
                        jsonDataBuilder.append("          \"image\": \"").append(imageLink).append("\"\n");
                        jsonDataBuilder.append("        },\n");

                    }catch(Exception e) {
                        // Handle any exceptions that might occur during the operation
                        e.printStackTrace();
                        //return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating document: " + e.getMessage());
                    }


                }
                i=0;

                // Remove the trailing comma from the last product object
                if (!productIds.isEmpty()) {
                    jsonDataBuilder.deleteCharAt(jsonDataBuilder.length() - 2); // Removes the last comma
                }

                // Append closing brackets for products array and bundle object
                jsonDataBuilder.append("      ]\n");
                jsonDataBuilder.append("    },\n");
            }

            // Remove the trailing comma from the last bundle object
            if (!querySnapshot.isEmpty()) {
                jsonDataBuilder.deleteCharAt(jsonDataBuilder.length() - 2); // Removes the last comma
            }

            // Append closing brackets for bundles array and JSON object
            jsonDataBuilder.append("  ]\n");
            jsonDataBuilder.append("}");

            // Return the JSON string in the response
            System.out.println(jsonDataBuilder.toString());
            return jsonDataBuilder.toString();
        } catch (InterruptedException | ExecutionException e) {
            // Handle exceptions appropriately
            e.printStackTrace();
            // Return an error response
            return "{\"error\": \"Failed to retrieve bundles\"}";
        }
    }

    @PostMapping("/api/addToCart")
    public ResponseEntity<String> addToCart(@RequestBody String bundleId) throws ExecutionException, InterruptedException {
        // Get the current user's ID
        var user = WebSecurityConfig.getUser();

        // Reference to the user's document
        DocumentReference userRef = db.collection("user").document(user.getEmail());

        // Reference to the bundle document
        DocumentReference bundleRef = db.collection("bundles").document(bundleId);

        // Get the bundle data
        ApiFuture<DocumentSnapshot> bundleFuture = bundleRef.get();
        DocumentSnapshot bundleSnapshot = bundleFuture.get();
        if (bundleSnapshot.exists()) {
            Map<String, Object> bundleData = bundleSnapshot.getData();

            // Add the bundle document to the basket subcollection under the user's document
            DocumentReference addedBundleRef = userRef.collection("basket").add(bundleData).get();
            // Wait for the result
            Map<String, Object> updatedBundleData = new HashMap<>();
            updatedBundleData.put("cartBundleId", addedBundleRef.getId());
            addedBundleRef.update(updatedBundleData);
            // Return a response
            return ResponseEntity.status(HttpStatus.CREATED).body("Bundle with ID: " + bundleId + " added to cart with ID: " + addedBundleRef.getId());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bundle with ID: " + bundleId + " does not exist");
        }
    }

    @GetMapping("/api/getCart")
    public List<Map<String, Object>> getCart() throws ExecutionException, InterruptedException {
        var user = WebSecurityConfig.getUser();

        // Reference to the user's document
        CollectionReference basketRef = db.collection("user").document(user.getEmail()).collection("basket");

        ApiFuture<QuerySnapshot> querySnapshot = basketRef.get();
        List<Map<String, Object>> shoppingCart = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
            Map<String, Object> itemData = document.getData();
            shoppingCart.add(itemData);
        }
        return shoppingCart;

    }

    @DeleteMapping("/api/removeFromCart")
    public ResponseEntity<String> removeFromCart(@RequestBody String bundleId) throws ExecutionException, InterruptedException {
        // Get the current user's ID
        var user = WebSecurityConfig.getUser();

        // Reference to the user's document
        CollectionReference userBasketRef = db.collection("user").document(user.getEmail()).collection("basket");

        DocumentReference bundleRef = userBasketRef.document(bundleId);


        bundleRef.delete();

        // Check if the document still exists after deletion
        boolean documentExists = bundleRef.get().get().exists();

        // Check if the deletion was successful
        if (!documentExists) {
            return ResponseEntity.status(HttpStatus.OK).body("Bundle with ID: " + bundleId + " removed from cart");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bundle with ID: " + bundleId + " does not exist");
        }
    }



    @GetMapping("/api/getProducts")
    public String getProducts() throws JsonProcessingException {
        WebClient webClient = webClientBuilder.build();

        StringBuilder jsonDataBuilder = new StringBuilder();
        jsonDataBuilder.append("{\n");
        jsonDataBuilder.append("  \"suppliers\": [\n");

        // Array of endpoint URLs
        String[] endpointURLs = {
                "http://sud.switzerlandnorth.cloudapp.azure.com:8080/products/",
                "http://ivan.canadacentral.cloudapp.azure.com:8080/products/",
                "http://sud.japaneast.cloudapp.azure.com:8080/products/"
        };


        // Loop through each endpoint
        for (String endpointURL : endpointURLs) {
            String responseBody = webClient.get()
                    .uri(endpointURL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Extract supplier name from endpoint URL
            String supplierName = endpointURL.substring(0,endpointURL.lastIndexOf('/') + 1);

            // Append supplier details to JSON
            jsonDataBuilder.append("    {\n");
            jsonDataBuilder.append("      \"name\": \"").append(supplierName).append("\",\n");
            jsonDataBuilder.append("      \"products\": [\n");

            // Process products from the response
            processProducts(responseBody, jsonDataBuilder);

            // Close products array and supplier object
            jsonDataBuilder.append("      ]\n");
            jsonDataBuilder.append("    }");

            // Add comma if there are more suppliers
            if (!endpointURL.equals(endpointURLs[endpointURLs.length - 1])) {
                jsonDataBuilder.append(",");
            }
            jsonDataBuilder.append("\n");
        }

        // Close suppliers array and JSON object
        jsonDataBuilder.append("  ]\n");
        jsonDataBuilder.append("}");

        // Print and return the JSON string
        String jsonString = jsonDataBuilder.toString();
        System.out.println(jsonString);
        return jsonString;
    }

    // Method to process products from the response and append to JSON
    private void processProducts(String responseBody, StringBuilder jsonDataBuilder) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode productListNode = rootNode.path("_embedded").path("productList");

            // Iterate over products and append to JSON
            for (JsonNode productNode : productListNode) {
                String productName = productNode.path("name").asText();
                double productPrice = productNode.path("price").asDouble();
                String productDescription = productNode.path("description").asText();
                String imageLink = productNode.path("imageLink").asText();

                // Append product details to the JSON string
                jsonDataBuilder.append("        {\n");
                jsonDataBuilder.append("          \"id\": \"").append(productNode.path("id").asText()).append("\",\n");
                jsonDataBuilder.append("          \"name\":  \"").append(productName).append("\",\n");
                jsonDataBuilder.append("          \"price\": ").append(productPrice).append(",\n");
                jsonDataBuilder.append("          \"description\": \"").append(productDescription).append("\",\n");
                jsonDataBuilder.append("          \"imageLink\": \"").append(imageLink).append("\"\n");
                jsonDataBuilder.append("        },\n");
            }

            // Remove the trailing comma from the last product object
            if (productListNode.size() > 0) {
                jsonDataBuilder.deleteCharAt(jsonDataBuilder.length() - 2); // Removes the last comma
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @PostMapping("/api/addBundle")
    public ResponseEntity<String> addBundle(
            @RequestParam("bundleTitle") String bundleTitle,
            @RequestParam("bundleDescription") String bundleDescription,
            @RequestParam("productIds") String productIds
    ) throws JsonProcessingException, ExecutionException, InterruptedException {
        var user = WebSecurityConfig.getUser();


        String productIdString = productIds.substring(1, productIds.length() - 1);
        String[] productIdSplit = productIdString.split(",");
        String[] productIdFinal = new String[productIdSplit.length];



        for (int i = 0; i < productIdSplit.length; i++) {
            productIdSplit[i] = productIdSplit[i].replaceAll("\"", "");
        }

        //CollectionReference products = db.collection("products");
        int i=0;

        for (String id: productIdSplit){

            String[] idParts = id.split("@");
            productIdFinal[i]=(idParts[1]);

            DocumentReference docRef = db.collection("products").document(idParts[1]);
            //System.out.println("TESTINGGG");

            ApiFuture<DocumentSnapshot> future = docRef.get();
            try {
                // Get the document snapshot
                DocumentSnapshot document = future.get();

                // Check if the document exists
                if (document.exists()) {
                    // Document exists
                    System.out.println("Document exists: " + document.getData());
                } else {
                    // Document doesn't exist, create it
                    Map<String, Object> data = new HashMap<>();
                    // Add data to the document as needed
                    data.put("field1", "value1");
                    data.put("field2", "value2");

                    // Asynchronously set the data for the document
                    ApiFuture<WriteResult> result = docRef.set(data);

                    // Wait for the set operation to complete
                    result.get();
                    System.out.println("Document created!");
                }
            } catch (InterruptedException | ExecutionException e) {
                // Handle any errors that may occur
                System.err.println("Error getting document: " + e.getMessage());
            }

            i++;
        }

        // Create a map to hold the data for the new document
        Map<String, Object> data = new HashMap<>();
        data.put("name", bundleTitle);
        data.put("description", bundleDescription);
        data.put("productIds", Arrays.asList(productIdFinal));
        data.put("price", "$XX");

        // Process bundle data
        String response = "Bundle Title: " + bundleTitle + "\n" +
                "Bundle Description: " + bundleDescription + "\n" +
                "Selected Product Ids: " + productIds + "\n";

        try {

            DocumentReference bundleRef = db.collection("bundles").document();


            // Set the data for the new document
            ApiFuture<WriteResult> writeResult = bundleRef.set(data);
            // Wait for the operation to complete
            writeResult.get();

            // Retrieve the Firestore-generated ID of the new document
            String bundleId = bundleRef.getId();

            // Return a success response with the ID of the newly created document
            return ResponseEntity.status(HttpStatus.CREATED).body("Bundle created with ID: " + bundleId);
        } catch (Exception e) {
            // Handle any exceptions that might occur during the operation
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating document: " + e.getMessage());
        }

    }


    @PostMapping("/api/updateBundle")
    public String updateBundle(
            @RequestParam("bundleId") String bundleId,
            @RequestParam("bundleTitle") String bundleTitle,
            @RequestParam("bundleDescription") String bundleDescription
    ) {
        System.out.println("I am in updateBundle");
        // Process updated bundle data
        String response = "Bundle ID: " + bundleId + "\n" +
                "Updated Bundle Title: " + bundleTitle + "\n" +
                "Updated Bundle Description: " + bundleDescription + "\n";

        System.out.println("Received updated bundle data:");
        System.out.println(response);

        return "Bundle updated successfully";
    }


}