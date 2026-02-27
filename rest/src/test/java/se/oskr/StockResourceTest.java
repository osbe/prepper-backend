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
class StockResourceTest {

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
  void getExpiredStockEmpty() {
    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/expired")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void getExpiredStockReturnsExpiredEntries() {
    long productId = createProduct("Water", "WATER", "LITERS", 10);
    String yesterday = LocalDate.now().minusDays(1).toString();
    createStockEntry(productId, 5, yesterday);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/expired")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].expiryDate", is(yesterday))
        .body("[0].expiryStatus", is("EXPIRED"));
  }

  @Test
  void getExpiredStockExcludesNonExpired() {
    long productId = createProduct("Preserved Food", "PRESERVED_FOOD", "CANS", 12);
    String yesterday = LocalDate.now().minusDays(1).toString();
    String nextYear = LocalDate.now().plusYears(1).toString();
    createStockEntry(productId, 3, yesterday);
    createStockEntry(productId, 3, nextYear);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/expired")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].expiryDate", is(yesterday));
  }

  @Test
  void getExpiringStockDefault() {
    long productId = createProduct("Medicine", "MEDICINE", "PIECES", 50);
    String in15Days = LocalDate.now().plusDays(15).toString();
    String in60Days = LocalDate.now().plusDays(60).toString();
    createStockEntry(productId, 10, in15Days);
    createStockEntry(productId, 10, in60Days);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/expiring")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].expiryDate", is(in15Days));
  }

  @Test
  void getExpiringStockCustomDays() {
    long productId = createProduct("Dry Goods", "DRY_GOODS", "KG", 20);
    String in5Days = LocalDate.now().plusDays(5).toString();
    String in10Days = LocalDate.now().plusDays(10).toString();
    createStockEntry(productId, 5, in5Days);
    createStockEntry(productId, 5, in10Days);

    given()
        .auth()
        .basic("admin", "admin")
        .queryParam("days", 7)
        .when()
        .get("/stock/expiring")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].expiryDate", is(in5Days));
  }

  @Test
  void getExpiringStockExcludesAlreadyExpired() {
    long productId = createProduct("Fuel", "FUEL", "LITERS", 30);
    String yesterday = LocalDate.now().minusDays(1).toString();
    createStockEntry(productId, 10, yesterday);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/expiring")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void getLowStockReturnsProductBelowTarget() {
    long productId = createProduct("Freeze Dried Meals", "FREEZE_DRIED", "PIECES", 10);
    createStockEntry(productId, 3, null);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/low")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].currentStock", is(3.0f))
        .body("[0].targetQuantity", is(10.0f));
  }

  @Test
  void getLowStockExcludesProductMeetingTarget() {
    long productId = createProduct("Salt", "DRY_GOODS", "KG", 10);
    createStockEntry(productId, 10, null);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/low")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void getLowStockNoStockAtAll() {
    createProduct("Water Reserves", "WATER", "LITERS", 100);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/stock/low")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].currentStock", is(0.0f))
        .body("[0].targetQuantity", is(100.0f));
  }

  @Test
  void updateStockEntryQuantity() {
    long productId = createProduct("Beans", "PRESERVED_FOOD", "CANS", 24);
    long entryId = createStockEntry(productId, 5, null);

    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {"quantity": 8}
            """)
        .when()
        .patch("/stock/{id}", entryId)
        .then()
        .statusCode(200)
        .body("quantity", is(8.0f));
  }

  @Test
  void updateNonExistentStockEntry() {
    given()
        .auth()
        .basic("admin", "admin")
        .contentType(ContentType.JSON)
        .body(
            """
            {"quantity": 5}
            """)
        .when()
        .patch("/stock/99999")
        .then()
        .statusCode(404);
  }

  @Test
  void deleteStockEntry() {
    long productId = createProduct("Rice", "DRY_GOODS", "KG", 20);
    long entryId = createStockEntry(productId, 5, null);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .delete("/stock/{id}", entryId)
        .then()
        .statusCode(204);

    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/products/{id}/stock", productId)
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void deleteNonExistentStockEntry() {
    given().auth().basic("admin", "admin").when().delete("/stock/99999").then().statusCode(404);
  }

  @Test
  void userCanReadStock() {
    given().auth().basic("user", "user").when().get("/stock/expired").then().statusCode(200);
  }

  @Test
  void userCannotPatchStock() {
    long productId = createProduct("Water", "WATER", "LITERS", 10);
    long entryId = createStockEntry(productId, 5, null);

    given()
        .auth()
        .basic("user", "user")
        .contentType(ContentType.JSON)
        .body(
            """
            {"quantity": 3}
            """)
        .when()
        .patch("/stock/{id}", entryId)
        .then()
        .statusCode(403);
  }

  @Test
  void userCannotDeleteStock() {
    long productId = createProduct("Water", "WATER", "LITERS", 10);
    long entryId = createStockEntry(productId, 5, null);

    given()
        .auth()
        .basic("user", "user")
        .when()
        .delete("/stock/{id}", entryId)
        .then()
        .statusCode(403);
  }

  @Test
  void expiryStatusNullForFarFuture() {
    long productId = createProduct("Freeze Dried", "FREEZE_DRIED", "PIECES", 10);
    String in60Days = LocalDate.now().plusDays(60).toString();
    createStockEntry(productId, 5, in60Days);

    given()
        .auth()
        .basic("admin", "admin")
        .queryParam("days", 90)
        .when()
        .get("/stock/expiring")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].expiryStatus", nullValue());
  }
}
