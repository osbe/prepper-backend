package se.oskr.core;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class Greeting extends PanacheEntity {

  public String message;

  @Override
  public String toString() {
    return "Greeting{id=" + id + ", message='" + message + "'}";
  }
}
