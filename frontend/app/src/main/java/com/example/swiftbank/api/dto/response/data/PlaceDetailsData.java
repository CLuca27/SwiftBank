package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class PlaceDetailsData {

    @SerializedName("formatted_address")
    private String formattedAddress;

    @SerializedName("street_number")
    private String streetNumber;

    @SerializedName("street")
    private String street;

    @SerializedName("city")
    private String city;

    @SerializedName("county")
    private String county;

    @SerializedName("postal_code")
    private String postalCode;

    public String getFormattedAddress() { return formattedAddress; }
    public String getStreetNumber() { return streetNumber; }
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getCounty() { return county; }
    public String getPostalCode() { return postalCode; }

    public String getFullStreet() {
        if (street == null) return null;
        if (streetNumber != null) {
            return street + " " + streetNumber;
        }
        return street;
    }
}