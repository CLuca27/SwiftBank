package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class ProfileData {
    @SerializedName("user_id")
    private int userId;

    @SerializedName("email")
    private String email;

    @SerializedName("phone")
    private String phone;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("birth_date")
    private String birthDate;

    @SerializedName("address")
    private String address;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("profile_photo")
    private String profilePhoto;

    public int getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getAddress() {
        return address;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }
}
