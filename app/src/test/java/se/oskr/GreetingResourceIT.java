package se.oskr;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

// Runs the same scenarios as GreetingResourceTest but against the packaged application.
@QuarkusIntegrationTest
class GreetingResourceIT {

  @Test
  void testHelloEndpoint() {
    given().when().get("/hello").then().statusCode(200).body(is("Hello from Quarkus REST"));
  }
}
