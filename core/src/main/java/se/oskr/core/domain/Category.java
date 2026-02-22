package se.oskr.core.domain;

import java.time.LocalDate;

public enum Category {
  WATER(
      "Rotate to daily use and refill with fresh stock",
      "Discard and replace immediately — do not consume"),
  PRESERVED_FOOD(
      "Move to everyday pantry rotation and repurchase for the stash",
      "Inspect packaging for damage, swelling, or odor before discarding"),
  DRY_GOODS(
      "Move to everyday cooking rotation and replace in stash",
      "Inspect for moisture, pests, or off-odor before discarding"),
  FREEZE_DRIED(
      "Include in regular meals to rotate — reorder replacement",
      "Check packaging integrity; if sealed and odor-free may still be usable"),
  MEDICINE(
      "Consult a pharmacist about replacement; do not let this lapse",
      "Dispose via pharmacy take-back program — do not use"),
  FUEL(
      "Use in an equipment or generator test run; refill with fresh stock",
      "Dispose at a hazardous waste facility — do not use in equipment"),
  OTHER("Review item condition and plan for rotation", "Assess condition and replace if necessary");

  private static final int APPROACHING_THRESHOLD_DAYS = 30;

  public final String approachingExpiryAction;
  public final String expiredAction;

  Category(String approachingExpiryAction, String expiredAction) {
    this.approachingExpiryAction = approachingExpiryAction;
    this.expiredAction = expiredAction;
  }

  /**
   * Returns the recommended action for a stock entry with the given expiry date, or {@code null} if
   * no action is needed yet.
   */
  public String recommendedAction(LocalDate expiryDate) {
    LocalDate today = LocalDate.now();
    if (expiryDate.isBefore(today)) {
      return expiredAction;
    }
    if (!expiryDate.isAfter(today.plusDays(APPROACHING_THRESHOLD_DAYS))) {
      return approachingExpiryAction;
    }
    return null;
  }
}
