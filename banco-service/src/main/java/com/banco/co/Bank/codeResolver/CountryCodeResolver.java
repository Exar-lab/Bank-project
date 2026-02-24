package com.banco.co.Bank.codeResolver;

import com.banco.co.Bank.exception.CountryCodeNotFoundException;
import com.banco.co.Bank.exception.InvalidCountryInputException;

import java.util.HashMap;
import java.util.Map;

public class CountryCodeResolver {

    private static final Map<String, String> COUNTRY_TO_CODE = new HashMap<>();

    static {
        // América
        COUNTRY_TO_CODE.put("Costa Rica", "CRC");
        COUNTRY_TO_CODE.put("United States", "USA");
        COUNTRY_TO_CODE.put("Mexico", "MEX");
        COUNTRY_TO_CODE.put("México", "MEX");
        COUNTRY_TO_CODE.put("Canada", "CAN");
        COUNTRY_TO_CODE.put("Canadá", "CAN");
        COUNTRY_TO_CODE.put("Brazil", "BRA");
        COUNTRY_TO_CODE.put("Brasil", "BRA");
        COUNTRY_TO_CODE.put("Argentina", "ARG");
        COUNTRY_TO_CODE.put("Chile", "CHL");
        COUNTRY_TO_CODE.put("Colombia", "COL");
        COUNTRY_TO_CODE.put("Peru", "PER");
        COUNTRY_TO_CODE.put("Perú", "PER");

        // Europa
        COUNTRY_TO_CODE.put("Spain", "ESP");
        COUNTRY_TO_CODE.put("España", "ESP");
        COUNTRY_TO_CODE.put("France", "FRA");
        COUNTRY_TO_CODE.put("Francia", "FRA");
        COUNTRY_TO_CODE.put("Germany", "DEU");
        COUNTRY_TO_CODE.put("Alemania", "DEU");
        COUNTRY_TO_CODE.put("Italy", "ITA");
        COUNTRY_TO_CODE.put("Italia", "ITA");
        COUNTRY_TO_CODE.put("United Kingdom", "GBR");
        COUNTRY_TO_CODE.put("Reino Unido", "GBR");

        // Más países...
    }

    public static String resolve(String countryName) {
        if (countryName == null || countryName.isBlank()) {
            throw new InvalidCountryInputException(countryName);
        }

        // Normalizar: trim y capitalizar
        String normalized = countryName.trim();

        // Buscar código
        String code = COUNTRY_TO_CODE.get(normalized);

        if (code == null) {
            // Intentar búsqueda case-insensitive
            code = COUNTRY_TO_CODE.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(normalized))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new CountryCodeNotFoundException(normalized));
        }

        return code;
    }

    public static String getCountryName(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidCountryInputException(code);
        }

        return COUNTRY_TO_CODE.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new CountryCodeNotFoundException(code));
    }
}