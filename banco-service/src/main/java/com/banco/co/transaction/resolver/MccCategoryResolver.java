package com.banco.co.transaction.resolver;

import com.banco.co.transaction.enums.TransactionCategory;

import java.util.HashMap;
import java.util.Map;

public class MccCategoryResolver {
    private static final Map<String, TransactionCategory> MCC_TO_CATEGORY = new HashMap<>();

    static {
        // --- ALIMENTACIÓN Y SUPERMERCADOS ---
        MCC_TO_CATEGORY.put("5812", TransactionCategory.FOOD_DINING); // Restaurantes
        MCC_TO_CATEGORY.put("5813", TransactionCategory.FOOD_DINING); // Bares y Discotecas
        MCC_TO_CATEGORY.put("5814", TransactionCategory.FOOD_DINING); // Comida Rápida
        MCC_TO_CATEGORY.put("5411", TransactionCategory.FOOD_DINING); // Supermercados
        MCC_TO_CATEGORY.put("5422", TransactionCategory.FOOD_DINING); // Carnicerías y Pescaderías
        MCC_TO_CATEGORY.put("5441", TransactionCategory.FOOD_DINING); // Confiterías y Panaderías

        // --- TRANSPORTE Y GASOLINERAS ---
        MCC_TO_CATEGORY.put("5541", TransactionCategory.TRANSPORTATION); // Estaciones de Servicio
        MCC_TO_CATEGORY.put("5542", TransactionCategory.TRANSPORTATION); // Dispensadores automáticos de combustible
        MCC_TO_CATEGORY.put("4121", TransactionCategory.TRANSPORTATION); // Taxis y Limusinas (Uber/Didi)
        MCC_TO_CATEGORY.put("4111", TransactionCategory.TRANSPORTATION); // Transporte local y suburbano (Tren, Bus)
        MCC_TO_CATEGORY.put("7523", TransactionCategory.TRANSPORTATION); // Estacionamientos (Parqueos)
        MCC_TO_CATEGORY.put("4789", TransactionCategory.TRANSPORTATION); // Peajes

        // --- SALUD Y BIENESTAR ---
        MCC_TO_CATEGORY.put("5912", TransactionCategory.HEALTH_FITNESS); // Farmacias
        MCC_TO_CATEGORY.put("8011", TransactionCategory.HEALTH_FITNESS); // Médicos y Clínicas
        MCC_TO_CATEGORY.put("8021", TransactionCategory.HEALTH_FITNESS); // Dentistas y Ortodoncistas
        MCC_TO_CATEGORY.put("7991", TransactionCategory.HEALTH_FITNESS); // Gimnasios y Centros deportivos

        // --- ENTRETENIMIENTO Y VIAJES ---
        MCC_TO_CATEGORY.put("7832", TransactionCategory.ENTERTAINMENT); // Cines
        MCC_TO_CATEGORY.put("7922", TransactionCategory.ENTERTAINMENT); // Teatros y Eventos
        MCC_TO_CATEGORY.put("3000", TransactionCategory.TRAVEL);        // Aerolíneas (Rango 3000-3299 usualmente)
        MCC_TO_CATEGORY.put("4511", TransactionCategory.TRAVEL);        // Aerolíneas y pasajes
        MCC_TO_CATEGORY.put("7011", TransactionCategory.TRAVEL);        // Hoteles y Moteles

        // --- SERVICIOS PÚBLICOS Y EDUCACIÓN ---
        MCC_TO_CATEGORY.put("4814", TransactionCategory.BILLS_UTILITIES); // Telecomunicaciones (Internet/Celular)
        MCC_TO_CATEGORY.put("4900", TransactionCategory.BILLS_UTILITIES); // Electricidad, Agua, Gas
        MCC_TO_CATEGORY.put("8211", TransactionCategory.EDUCATION);       // Escuelas Primarias y Secundarias
        MCC_TO_CATEGORY.put("8220", TransactionCategory.EDUCATION);       // Universidades y Colegios

        // --- COMPRAS Y OTROS ---
        MCC_TO_CATEGORY.put("5311", TransactionCategory.SHOPPING); // Grandes Almacenes
        MCC_TO_CATEGORY.put("5942", TransactionCategory.SHOPPING); // Librerías
        MCC_TO_CATEGORY.put("5732", TransactionCategory.SHOPPING); // Tiendas de Electrónica
    }

    public static TransactionCategory resolve(String mccCode) {
        return MCC_TO_CATEGORY.getOrDefault(mccCode, TransactionCategory.OTHER);
    }
}
