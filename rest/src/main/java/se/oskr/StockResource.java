package se.oskr;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import se.oskr.api.StockApi;
import se.oskr.core.domain.Product;
import se.oskr.core.domain.StockEntry;
import se.oskr.core.service.ProductService;
import se.oskr.core.service.StockService;
import se.oskr.model.StockEntryPatch;
import se.oskr.model.StockEntryRequest;

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

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("admin")
  public se.oskr.model.StockEntry replaceStockEntry(
      @PathParam("id") Long id, StockEntryRequest body) {
    return stockService
        .update(
            id,
            body.getQuantity(),
            body.getSubType(),
            body.getPurchasedDate(),
            body.getExpiryDate(),
            body.getLocation(),
            body.getNotes())
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
    dto.setSubType(e.subType);
    dto.setPurchasedDate(e.purchasedDate);
    dto.setExpiryDate(e.expiryDate);
    dto.setLocation(e.location);
    dto.setNotes(e.notes);
    String status = e.product.category.expiryStatus(e.expiryDate);
    dto.setExpiryStatus(status != null ? se.oskr.model.ExpiryStatus.fromValue(status) : null);
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
