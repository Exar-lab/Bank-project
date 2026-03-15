package com.banco.co.auditLog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class AuditLogDetail {
    @Column(name = "detail_key")
    private String key;

    @Column(name = "detail_value", columnDefinition = "TEXT")
    private String value;

    public AuditLogDetail(String key, Object value) {
        this.key = key;
        this.value = value != null ? value.toString() : null;
    }
}
