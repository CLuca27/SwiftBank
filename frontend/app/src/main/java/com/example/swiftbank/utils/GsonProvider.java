package com.example.swiftbank.utils;

import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.transaction.Transaction;
import com.example.swiftbank.api.dto.transaction.TransactionDeserializer;
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

