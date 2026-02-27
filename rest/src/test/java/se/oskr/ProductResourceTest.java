package se.oskr;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProductResourceTest {

  private final List<Long> createdProductIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (Long id : createdProductIds) {
      given().auth().basic("admin", "admin").delete("/products/{id}", id);
    }
    createdProductIds.clear();
  }

  private long createProduct(String name, String category, String unit, double target) {
    long id =
        given()
            .auth()
            .basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(
                String.format(
                    "{\"name\": \"%s\", \"category\": \"%s\", \"unit\": \"%s\","
                        + " \"targetQuantity\": %s}",
                    name, category, unit, target))
            .when()
            .post("/products")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getLong("id");
    createdProductIds.add(id);
    return id;
  }

  private long createStockEntry(long productId, double qty, String expiryDate) {
    String body =
        expiryDate != null
            ? String.format("{\"quantity\": %s, \"expiryDate\": \"%s\"}", qty, expiryDate)
            : String.format("{\"quantity\": %s}", qty);
    return given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  @Test
  void listProductsReturnsEmptyInitially() {
    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void createAndGetProduct() {
    long id =
        given()
            .auth()
            .basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "name": "Mineral Water",
                  "category": "WATER",
                  "unit": "LITERS",
                  "targetQuantity": 100
                }
                """)
            .when()
            .post("/products")
            .then()
            .statusCode(200)
            .body("name", is("Mineral Water"))
            .body("category", is("WATER"))
            .body("currentStock", is(0.0f))
            .extract()
            .jsonPath()
            .getLong("id");

    createdProductIds.add(id);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products/{id}", id)
        .then()
        .statusCode(200)
        .body("name", is("Mineral Water"));

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .delete("/products/{id}", id)
        .then()
        .statusCode(204);
  }

  @Test
  void getNonExistentProductReturns404() {
    given().auth().basic("admin", "admin").when().get("/products/99999").then().statusCode(404);
  }

  @Test
  void listProductsWithCategoryFilter() {
    createProduct("Water Jug", "WATER", "LITERS", 50);
    createProduct("Aspirin", "MEDICINE", "PIECES", 100);

    given()
        .auth()
        .basic("admin", "admin")
        .queryParam("category", "WATER")
        .when()
        .get("/products")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].category", is("WATER"));
  }

  @Test
  void updateProduct() {
    long id = createProduct("Old Name", "WATER", "LITERS", 10);

    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "name": "New Name",
              "category": "MEDICINE",
              "unit": "PIECES",
              "targetQuantity": 20,
              "notes": "Updated"
            }
            """)
        .when()
        .put("/products/{id}", id)
        .then()
        .statusCode(200)
        .body("name", is("New Name"))
        .body("category", is("MEDICINE"))
        .body("unit", is("PIECES"))
        .body("targetQuantity", is(20.0f))
        .body("notes", is("Updated"));
  }

  @Test
  void updateNonExistentProduct() {
    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {"name": "X", "category": "WATER", "unit": "LITERS", "targetQuantity": 1}
            """)
        .when()
        .put("/products/99999")
        .then()
        .statusCode(404);
  }

  @Test
  void deleteNonExistentProduct() {
    given().auth().basic("admin", "admin").when().delete("/products/99999").then().statusCode(404);
  }

  @Test
  void listProductStock() {
    long productId = createProduct("Rice", "DRY_GOODS", "KG", 20);
    createStockEntry(productId, 5, null);
    createStockEntry(productId, 3, null);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .body("$", hasSize(2));
  }

  @Test
  void listProductStockNonExistentProduct() {
    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products/99999/stock")
        .then()
        .statusCode(404);
  }

  @Test
  void createStockEntryAllFields() {
    long productId = createProduct("Beans", "PRESERVED_FOOD", "CANS", 24);
    String purchasedDate = LocalDate.now().minusMonths(1).toString();
    String expiryDate = LocalDate.now().plusYears(2).toString();

    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            String.format(
                """
                {
                  "quantity": 12,
                  "subType": "Kidney Beans",
                  "purchasedDate": "%s",
                  "expiryDate": "%s",
                  "location": "Cellar",
                  "notes": "Brand A"
                }
                """,
                purchasedDate, expiryDate))
        .when()
        .post("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .body("productId", is((int) productId))
        .body("quantity", is(12.0f))
        .body("subType", is("Kidney Beans"))
        .body("purchasedDate", is(purchasedDate))
        .body("expiryDate", is(expiryDate))
        .body("location", is("Cellar"))
        .body("notes", is("Brand A"))
        .body("expiryStatus", nullValue());
  }

  @Test
  void createStockEntryMinimalFields() {
    long productId = createProduct("Salt", "DRY_GOODS", "KG", 5);

    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {"quantity": 2}
            """)
        .when()
        .post("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .body("quantity", is(2.0f))
        .body("subType", nullValue())
        .body("purchasedDate", nullValue())
        .body("expiryDate", nullValue())
        .body("location", nullValue())
        .body("notes", nullValue())
        .body("expiryStatus", nullValue());
  }

  @Test
  void createStockEntryNonExistentProduct() {
    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {"quantity": 5}
            """)
        .when()
        .post("/products/99999/stock")
        .then()
        .statusCode(404);
  }

  @Test
  void stockOrderedByExpiryDate() {
    long productId = createProduct("Fuel Can", "FUEL", "LITERS", 50);
    String earlyDate = LocalDate.now().plusDays(5).toString();
    String lateDate = LocalDate.now().plusDays(60).toString();
    createStockEntry(productId, 10, lateDate);
    createStockEntry(productId, 10, null);
    createStockEntry(productId, 10, earlyDate);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .body("$", hasSize(3))
        .body("[0].expiryDate", is(earlyDate))
        .body("[1].expiryDate", is(lateDate))
        .body("[2].expiryDate", nullValue());
  }

  @Test
  void userCanReadProducts() {
    given().auth().basic("user", "user").when().get("/products").then().statusCode(200);
  }

  @Test
  void userCannotCreateProduct() {
    given()
        .auth()
        .basic("user", "user")
        .contentType(ContentType.JSON)
        .body(
            """
            {"name": "X", "category": "WATER", "unit": "LITERS", "targetQuantity": 1}
            """)
        .when()
        .post("/products")
        .then()
        .statusCode(403);
  }

  @Test
  void unauthenticatedIsRejected() {
    given().when().get("/products").then().statusCode(401);
  }
}
