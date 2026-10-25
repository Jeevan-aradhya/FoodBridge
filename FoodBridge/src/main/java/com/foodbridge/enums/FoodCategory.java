package com.foodbridge.enums;

public enum FoodCategory {
    COOKED_MEALS("Cooked meals"),
    BAKERY("Bakery"),
    FRUITS_VEGETABLES("Fruits & vegetables"),
    PACKAGED("Packaged food"),
    DAIRY("Dairy"),
    BEVERAGES("Beverages"),
    OTHER("Other");

    private final String label;

    FoodCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
