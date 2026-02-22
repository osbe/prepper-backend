package se.oskr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import se.oskr.api.ProductsApi;
import se.oskr.core.domain.Category;
import se.oskr.core.domain.Product;
import se.oskr.core.domain.StockEntry;
import se.oskr.core.domain.Unit;
import se.oskr.core.service.ProductService;
import se.oskr.core.service.StockService;
import se.oskr.model.ProductRequest;
import se.oskr.model.StockEntryRequest;

@ApplicationScoped
public class ProductResource implements ProductsApi {

  @Inject ProductService productService;

  @Inject StockService stockService;

  @Override
  public List<se.oskr.model.Product> listProducts(se.oskr.model.Category category) {
    var coreCategory = category != null ? Category.valueOf(category.name()) : null;
    return productService.list(coreCategory).stream().map(this::toProductDto).toList();
  }

  @Override
  public se.oskr.model.Product createProduct(ProductRequest body) {
    var product =
        productService.create(
            body.getName(),
            Category.valueOf(body.getCategory().name()),
            Unit.valueOf(body.getUnit().name()),
            body.getTargetQuantity(),
            body.getNotes());
    return toProductDto(product);
  }

  @Override
  public se.oskr.model.Product getProduct(Long id) {
    return productService.findById(id).map(this::toProductDto).orElseThrow(NotFoundException::new);
  }

  @Override
  public se.oskr.model.Product updateProduct(Long id, ProductRequest body) {
    return productService
        .update(
            id,
            body.getName(),
            Category.valueOf(body.getCategory().name()),
            Unit.valueOf(body.getUnit().name()),
            body.getTargetQuantity(),
            body.getNotes())
        .map(this::toProductDto)
        .orElseThrow(NotFoundException::new);
  }

  @Override
  public void deleteProduct(Long id) {
    if (!productService.delete(id)) {
      throw new NotFoundException();
    }
  }

  @Override
  public List<se.oskr.model.StockEntry> listProductStock(Long id) {
    if (productService.findById(id).isEmpty()) {
      throw new NotFoundException();
    }
    return stockService.listForProduct(id).stream().map(this::toStockEntryDto).toList();
  }

  @Override
  public se.oskr.model.StockEntry createStockEntry(Long id, StockEntryRequest body) {
    return stockService
        .create(
            id,
            body.getQuantity(),
            body.getPurchasedDate(),
            body.getExpiryDate(),
            body.getLocation(),
            body.getNotes())
        .map(this::toStockEntryDto)
        .orElseThrow(NotFoundException::new);
  }

  private se.oskr.model.Product toProductDto(Product p) {
    se.oskr.model.Product dto = new se.oskr.model.Product();
    dto.setId(p.id);
    dto.setName(p.name);
    dto.setCategory(se.oskr.model.Category.valueOf(p.category.name()));
    dto.setUnit(se.oskr.model.Unit.valueOf(p.unit.name()));
    dto.setTargetQuantity(p.targetQuantity);
    dto.setCurrentStock(productService.currentStock(p.id));
    dto.setNotes(p.notes);
    return dto;
  }

  private se.oskr.model.StockEntry toStockEntryDto(StockEntry e) {
    se.oskr.model.StockEntry dto = new se.oskr.model.StockEntry();
    dto.setId(e.id);
    dto.setProductId(e.product.id);
    dto.setQuantity(e.quantity);
    dto.setPurchasedDate(e.purchasedDate);
    dto.setExpiryDate(e.expiryDate);
    dto.setLocation(e.location);
    dto.setNotes(e.notes);
    return dto;
  }
}
