package se.oskr.core.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

@Entity
public class StockEntry extends PanacheEntity {

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  public Product product;

  @Column(nullable = false)
  public double quantity;

  public String subType;

  public LocalDate purchasedDate;

  public LocalDate expiryDate;

  public String location;

  public String notes;
}
