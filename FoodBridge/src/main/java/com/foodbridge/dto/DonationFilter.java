package com.foodbridge.dto;

import lombok.Getter;
import lombok.Setter;

/** Query-string filters for the donation list (web page and API). */
@Getter
@Setter
public class DonationFilter {
    private String q;
    private String category;
    private String city;
    private Integer minQuantity;
    /** AVAILABLE (default) or ALL. */
    private String availability = "AVAILABLE";
    /** Only show donations whose pickup deadline is within this many hours. */
    private Integer withinHours;
    /** deadline (default), newest, quantity. */
    private String sort = "deadline";
    private int page = 0;
    private int size = 9;
}
