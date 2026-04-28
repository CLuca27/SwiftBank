package com.example.swiftbank.config;

import com.example.swiftbank.api.dto.response.data.error.ErrorDataDeserializer;
import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.response.data.success.transaction.Transaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.TransactionDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonProvider {
    private static Gson gson;

    public static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .registerTypeAdapter(ErrorData.class, new ErrorDataDeserializer())
                    .registerTypeAdapter(Transaction.class, new TransactionDeserializer())
                    .create();
        }

        return gson;
    }
}

