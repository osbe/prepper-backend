package se.oskr;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

// Runs against the packaged application.
@QuarkusIntegrationTest
class ProductResourceIT {

  @Test
  void listProductsReturnsEmptyInitially() {
    given().when().get("/products").then().statusCode(200).body("$", hasSize(0));
  }
}
