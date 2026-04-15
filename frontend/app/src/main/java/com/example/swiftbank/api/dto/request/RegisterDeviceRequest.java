package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class RegisterDeviceRequest {

    @SerializedName("fcm_token")
    private String fcmToken;

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("device_name")
    private String deviceName;

    public RegisterDeviceRequest(String fcmToken, String deviceId, String deviceName) {
        this.fcmToken = fcmToken;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
