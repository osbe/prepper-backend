package se.oskr.core.domain;

import java.time.LocalDate;

public enum Category {
  WATER,
  PRESERVED_FOOD,
  DRY_GOODS,
  FREEZE_DRIED,
  MEDICINE,
  FUEL,
  OTHER;

  private static final int APPROACHING_THRESHOLD_DAYS = 30;

  /**
   * Returns {@code "EXPIRED"}, {@code "APPROACHING"}, or {@code null} based on the expiry date.
   * The frontend uses this key together with the category for i18n lookups.
   */
  public String expiryStatus(LocalDate expiryDate) {
    if (expiryDate == null) {
      return null;
    }
    LocalDate today = LocalDate.now();
    if (expiryDate.isBefore(today)) {
      return "EXPIRED";
    }
    if (!expiryDate.isAfter(today.plusDays(APPROACHING_THRESHOLD_DAYS))) {
      return "APPROACHING";
    }
    return null;
  }
}
