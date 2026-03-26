package com.banco.co.fraud.enums;

public enum FraudAnalysisResult {
    CLEAR,       // Transaction is normal — no action needed
    SUSPICIOUS,  // Flagged for review — logged, Phase 3: alert compliance
    BLOCKED      // Exceeds absolute threshold — logged, Phase 3: reverse transaction
}
