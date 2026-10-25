package com.foodbridge.enums;

/**
 * AVAILABLE : some quantity can still be requested/reserved.
 * RESERVED  : all quantity is reserved by accepted requests, waiting for delivery.
 * COMPLETED : everything reserved was delivered.
 * EXPIRED   : pickup deadline passed before everything was reserved.
 * DISABLED  : hidden by an administrator.
 * WITHDRAWN : removed by the donor.
 */
public enum DonationStatus { AVAILABLE, RESERVED, COMPLETED, EXPIRED, DISABLED, WITHDRAWN }
