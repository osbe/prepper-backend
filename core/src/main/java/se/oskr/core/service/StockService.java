package se.oskr.core.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import se.oskr.core.domain.Product;
import se.oskr.core.domain.StockEntry;

@ApplicationScoped
public class StockService {

  @Transactional
  public List<StockEntry> listForProduct(long productId) {
    return StockEntry.list("product.id = ?1 ORDER BY expiryDate ASC", productId);
  }

  @Transactional
  public Optional<StockEntry> create(
      long productId,
      double quantity,
      LocalDate purchasedDate,
      LocalDate expiryDate,
      String location,
      String notes) {
    return Product.<Product>findByIdOptional(productId)
        .map(
            product -> {
              StockEntry entry = new StockEntry();
              entry.product = product;
              entry.quantity = quantity;
              entry.purchasedDate = purchasedDate;
              entry.expiryDate = expiryDate;
              entry.location = location;
              entry.notes = notes;
              entry.persist();
              return entry;
            });
  }

  @Transactional
  public Optional<StockEntry> updateQuantity(long id, double quantity) {
    return StockEntry.<StockEntry>findByIdOptional(id)
        .map(
            entry -> {
              entry.quantity = quantity;
              return entry;
            });
  }

  @Transactional
  public boolean delete(long id) {
    return StockEntry.deleteById(id);
  }

  @Transactional
  public List<StockEntry> listExpiring(int days) {
    LocalDate cutoff = LocalDate.now().plusDays(days);
    return StockEntry.list("expiryDate <= ?1 ORDER BY expiryDate ASC", cutoff);
  }
}
