package se.oskr.core.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import se.oskr.core.domain.Category;
import se.oskr.core.domain.Product;
import se.oskr.core.domain.StockEntry;
import se.oskr.core.domain.Unit;

@ApplicationScoped
public class ProductService {

  @Transactional
  public List<Product> list(Category category) {
    if (category != null) {
      return Product.list("category", category);
    }
    return Product.listAll();
  }

  @Transactional
  public Product create(
      String name, Category category, Unit unit, double targetQuantity, String notes) {
    Product product = new Product();
    product.name = name;
    product.category = category;
    product.unit = unit;
    product.targetQuantity = targetQuantity;
    product.notes = notes;
    product.persist();
    return product;
  }

  @Transactional
  public Optional<Product> findById(long id) {
    return Product.findByIdOptional(id);
  }

  @Transactional
  public Optional<Product> update(
      long id, String name, Category category, Unit unit, double targetQuantity, String notes) {
    return Product.<Product>findByIdOptional(id)
        .map(
            product -> {
              product.name = name;
              product.category = category;
              product.unit = unit;
              product.targetQuantity = targetQuantity;
              product.notes = notes;
              return product;
            });
  }

  @Transactional
  public boolean delete(long id) {
    StockEntry.delete("product.id", id);
    return Product.deleteById(id);
  }

  @Transactional
  public double currentStock(long productId) {
    Object result =
        StockEntry.getEntityManager()
            .createQuery(
                "SELECT COALESCE(SUM(s.quantity), 0.0) FROM StockEntry s WHERE s.product.id = :id")
            .setParameter("id", productId)
            .getSingleResult();
    return ((Number) result).doubleValue();
  }

  @Transactional
  public List<Product> listLowStock() {
    return Product.<Product>listAll().stream()
        .filter(p -> currentStock(p.id) < p.targetQuantity)
        .toList();
  }
}
