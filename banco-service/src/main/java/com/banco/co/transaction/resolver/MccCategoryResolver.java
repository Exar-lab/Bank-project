package com.banco.co.transaction.resolver;

import com.banco.co.transaction.enums.TransactionCategory;

import java.util.HashMap;
import java.util.Map;

public class MccCategoryResolver {
    private static final Map<String, TransactionCategory> MCC_TO_CATEGORY = new HashMap<>();

    static {
        // Restaurantes (5812, 5814)
        MCC_TO_CATEGORY.put("5812", TransactionCategory.FOOD_DINING);
        MCC_TO_CATEGORY.put("5814", TransactionCategory.FOOD_DINING);

        // Gasolinerías (5541, 5542)
        MCC_TO_CATEGORY.put("5541", TransactionCategory.TRANSPORTATION);
        MCC_TO_CATEGORY.put("5542", TransactionCategory.TRANSPORTATION);

        // Supermercados (5411, 5422)
        MCC_TO_CATEGORY.put("5411", TransactionCategory.FOOD_DINING);
        MCC_TO_CATEGORY.put("5422", TransactionCategory.FOOD_DINING);

        // Farmacias (5912)
        MCC_TO_CATEGORY.put("5912", TransactionCategory.HEALTH_FITNESS);

        // Más códigos MCC...
    }

    public static TransactionCategory resolve(String mccCode) {
        return MCC_TO_CATEGORY.getOrDefault(mccCode, TransactionCategory.OTHER);
    }
}
