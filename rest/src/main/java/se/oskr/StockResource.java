package se.oskr;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import se.oskr.api.StockApi;
import se.oskr.core.domain.Product;
import se.oskr.core.domain.StockEntry;
import se.oskr.core.service.ProductService;
import se.oskr.core.service.StockService;
import se.oskr.model.StockEntryPatch;

@ApplicationScoped
public class StockResource implements StockApi {

  @Inject StockService stockService;

  @Inject ProductService productService;

  @Override
  @RolesAllowed({"user", "admin"})
  public List<se.oskr.model.StockEntry> getExpiredStock() {
    return stockService.listExpired().stream().map(this::toStockEntryDto).toList();
  }

  @Override
  @RolesAllowed({"user", "admin"})
  public List<se.oskr.model.StockEntry> getExpiringStock(Integer days) {
    int d = days != null ? days : 30;
    return stockService.listExpiring(d).stream().map(this::toStockEntryDto).toList();
  }

  @Override
  @RolesAllowed({"user", "admin"})
  public List<se.oskr.model.Product> getLowStock() {
    return productService.listLowStock().stream().map(this::toProductDto).toList();
  }

  @Override
  @RolesAllowed("admin")
  public se.oskr.model.StockEntry updateStockEntry(Long id, StockEntryPatch body) {
    return stockService
        .updateQuantity(id, body.getQuantity())
        .map(this::toStockEntryDto)
        .orElseThrow(NotFoundException::new);
  }

  @Override
  @RolesAllowed("admin")
  public void deleteStockEntry(Long id) {
    if (!stockService.delete(id)) {
      throw new NotFoundException();
    }
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
    dto.setRecommendedAction(e.product.category.recommendedAction(e.expiryDate));
    return dto;
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
}
