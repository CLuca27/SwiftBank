package com.example.swiftbank.api.dto.response.data.error;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class ErrorDataDeserializer implements JsonDeserializer{


    @Override
    public ErrorData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();
        JsonElement codeElement = obj.get("code");
        if(codeElement == null || codeElement.isJsonNull())
            throw new JsonParseException("Missing error code");

        String code = codeElement.getAsString();

         Class<? extends ErrorData> targetClass = switch(code) {
             case "OTP_COOLDOWN" -> OtpCooldownErrorData.class;
             case "OTP_INVALID" -> AttemptsErrorData.class;
             case "ACCOUNT_LOCKED" -> LoginCooldownErrorData.class;
             case "INVALID_PIN" -> AttemptsErrorData.class;
             default -> SimpleErrorData.class;
         };

         return context.deserialize(json, targetClass);
    }
}
