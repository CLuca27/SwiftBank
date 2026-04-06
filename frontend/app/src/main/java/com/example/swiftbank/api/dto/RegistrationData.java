package com.example.swiftbank.api.dto;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class RegistrationData implements Parcelable {


    private String phoneNumber;
    private String email;
    private String firstName;
    private String lastName;
    private String cnp;
    private String address;

    private boolean phoneVerified;
    private boolean emailVerified;

    public RegistrationData() {}

    @Override
    public int describeContents() {
        return 0;
    }

    protected RegistrationData(Parcel in)
    {
        phoneNumber = in.readString();
        email = in.readString();
        firstName = in.readString();
        lastName = in.readString();
        cnp = in.readString();
        address = in.readString();
        phoneVerified = in.readByte() != 0;
        emailVerified = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(phoneNumber);
        dest.writeString(email);
        dest.writeString(firstName);
        dest.writeString(lastName);
        dest.writeString(cnp);
        dest.writeString(address);
        dest.writeByte((byte) (phoneVerified ? 1 : 0));
        dest.writeByte((byte) (emailVerified ? 1 : 0));

    }
    public static final Creator<RegistrationData> CREATOR = new Creator<RegistrationData>() {

        @Override
        public RegistrationData createFromParcel(Parcel in) {
            return new RegistrationData(in);
        }

        @Override
        public RegistrationData[] newArray(int size) {
            return new RegistrationData[size];
        }
    };

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCnp() {
        return cnp;
    }

    public void setCnp(String cnp) {
        this.cnp = cnp;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
