package com.banco.co.transaction.utils.metadata;

import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionMetadataEnricher implements ITransactionMetadataEnricher {

    public void enrich(Transaction transaction, HttpServletRequest request, TransactionChannel channel) {
        transaction.setChannel(channel);

        String ip = getClientIP(request);
        transaction.setIpAddress(ip);

        String userAgent = request.getHeader("User-Agent");
        transaction.setUserAgent(userAgent);

        transaction.setDeviceId(request.getHeader("X-Device-Id"));
        transaction.setDeviceType(inferDeviceType(userAgent));

        log.debug("Transaction enriched with metadata: IP={}, Device={}", ip, transaction.getDeviceType());
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String inferDeviceType(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "MOBILE";
        if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
        return "DESKTOP";
    }
}
