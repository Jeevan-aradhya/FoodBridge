package com.foodbridge.enums;

public enum FoodUnit {
    PORTIONS("portions"), KG("kg"), LITRES("litres"), PACKS("packs"), ITEMS("items");

    private final String label;

    FoodUnit(String label) { this.label = label; }

    public String getLabel() { return label; }
}
