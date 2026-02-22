package se.oskr.core.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Product extends PanacheEntity {

  @Column(nullable = false)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Category category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Unit unit;

  @Column(nullable = false)
  public double targetQuantity;

  public String notes;
}
