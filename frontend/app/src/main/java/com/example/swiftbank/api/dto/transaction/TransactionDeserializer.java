package com.example.swiftbank.api.dto.transaction;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class TransactionDeserializer implements JsonDeserializer<Transaction> {

    @Override
    public Transaction deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        JsonObject jsonObject = json.getAsJsonObject();

        if (!jsonObject.has("transaction_type")) {
            throw new JsonParseException("Missing transaction_type field");
        }

        String type = jsonObject.get("transaction_type").getAsString();

        switch (type) {
            case "CARD":
                return context.deserialize(json, CardTransaction.class);

            case "TRANSFER_IN":
            case "TRANSFER_OUT":
                return context.deserialize(json, TransferTransaction.class);

            case "BILL":
                return context.deserialize(json, BillTransaction.class);

            default:
                throw new JsonParseException("Unknown transaction type: " + type);
        }
    }
}
