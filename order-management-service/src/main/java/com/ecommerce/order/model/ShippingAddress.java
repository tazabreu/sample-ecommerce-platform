package com.ecommerce.order.model;

import java.util.Map;

/**
 * Value object representing a shipping address stored as JSONB in the database.
 * This follows Spring Data JDBC best practices for complex types.
 */
public class ShippingAddress {

    private final String street;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;

    public ShippingAddress(String street, String city, String state, String postalCode, String country) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
    }

    public ShippingAddress(Map<String, String> addressMap) {
        this.street = addressMap.get("street");
        this.city = addressMap.get("city");
        this.state = addressMap.get("state");
        this.postalCode = addressMap.get("postalCode");
        this.country = addressMap.get("country");
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    /**
     * Converts this shipping address to a Map for JSON serialization.
     *
     * @return Map representation of the shipping address
     */
    public Map<String, String> toMap() {
        return Map.of(
            "street", street != null ? street : "",
            "city", city != null ? city : "",
            "state", state != null ? state : "",
            "postalCode", postalCode != null ? postalCode : "",
            "country", country != null ? country : ""
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShippingAddress that)) return false;

        return street != null ? street.equals(that.street) : that.street == null;
    }

    @Override
    public int hashCode() {
        return street != null ? street.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ShippingAddress{" +
                "street='" + street + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", postalCode='" + postalCode + '\'' +
                ", country='" + country + '\'' +
                '}';
    }
}
