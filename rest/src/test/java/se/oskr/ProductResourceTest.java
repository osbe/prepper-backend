package se.oskr;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProductResourceTest {

  @Test
  void listProductsReturnsEmptyInitially() {
    given().when().get("/products").then().statusCode(200).body("$", hasSize(0));
  }

  @Test
  void createAndGetProduct() {
    var id =
        given()
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
            .path("id");

    given()
        .when()
        .get("/products/{id}", id)
        .then()
        .statusCode(200)
        .body("name", is("Mineral Water"));

    given().when().delete("/products/{id}", id).then().statusCode(204);
  }

  @Test
  void getNonExistentProductReturns404() {
    given().when().get("/products/99999").then().statusCode(404);
  }
}
