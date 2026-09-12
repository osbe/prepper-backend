package se.oskr;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MeResourceTest {

  @Test
  void adminSeesBothRolesAndCanWrite() {
    given()
        .auth()
        .basic("admin", "admin")
        .when()
        .get("/me")
        .then()
        .statusCode(200)
        .body("username", is("admin"))
        .body("roles", hasSize(2))
        .body("roles", containsInAnyOrder("admin", "user"))
        .body("canWrite", is(true));
  }

  @Test
  void userSeesOnlyReadRoleAndCannotWrite() {
    given()
        .auth()
        .basic("user", "user")
        .when()
        .get("/me")
        .then()
        .statusCode(200)
        .body("username", is("user"))
        .body("roles", containsInAnyOrder("user"))
        .body("canWrite", is(false));
  }

  @Test
  void unauthenticatedIsRejected() {
    given().when().get("/me").then().statusCode(401);
  }

  @Test
  void wrongPasswordIsRejected() {
    given().auth().basic("admin", "not-the-password").when().get("/me").then().statusCode(401);
  }
}
