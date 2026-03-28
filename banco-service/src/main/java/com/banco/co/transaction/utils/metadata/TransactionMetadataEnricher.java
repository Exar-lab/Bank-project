package com.banco.co.transaction.utils.metadata;

import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionMetadataEnricher implements ITransactionMetadataEnricher {

    @Override
    public void enrich(Transaction transaction, TransactionRequestMetadataDto metadata, TransactionChannel channel) {
        transaction.setChannel(channel);
        transaction.setIpAddress(metadata.ipAddress());
        transaction.setUserAgent(metadata.userAgent());
        transaction.setDeviceId(metadata.deviceId());
        transaction.setDeviceType(inferDeviceType(metadata.userAgent()));

        log.debug("Transaction enriched with metadata: IP={}, Device={}", metadata.ipAddress(), transaction.getDeviceType());
    }

    private String inferDeviceType(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "MOBILE";
        if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
        return "DESKTOP";
    }
}
