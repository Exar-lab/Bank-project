INSERT INTO refresh_tokens (
    id,
    user_id,
    jti,
    token_hash,
    expires_at,
    created_at,
    revoked,
    parent_jti,
    ip_address,
    user_agent,
    device_id
)
SELECT
    UUID_TO_BIN(UUID()) AS id,
    uc.user_id,
    COALESCE(
        JSON_UNQUOTE(
            JSON_EXTRACT(
                CONVERT(
                    FROM_BASE64(
                        CONCAT(
                            REPLACE(
                                REPLACE(
                                    SUBSTRING_INDEX(SUBSTRING_INDEX(uc.refresh_token, '.', 2), '.', -1),
                                    '-',
                                    '+'
                                ),
                                '_',
                                '/'
                            ),
                            REPEAT(
                                '=',
                                (4 - MOD(CHAR_LENGTH(SUBSTRING_INDEX(SUBSTRING_INDEX(uc.refresh_token, '.', 2), '.', -1)), 4)) % 4
                            )
                        )
                    ) USING utf8mb4
                ),
                '$.jti'
            )
        ),
        SHA2(uc.refresh_token, 256)
    ) AS jti,
    SHA2(uc.refresh_token, 256) AS token_hash,
    uc.refresh_token_expiry,
    COALESCE(uc.updated_at, uc.created_at, NOW(6)) AS created_at,
    CASE
        WHEN uc.refresh_token_expiry IS NULL OR uc.refresh_token_expiry < NOW(6) THEN 1
        ELSE 0
    END AS revoked,
    NULL,
    NULL,
    NULL,
    NULL
FROM user_credentials uc
WHERE uc.refresh_token IS NOT NULL
  AND uc.refresh_token <> ''
  AND uc.refresh_token_expiry IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM refresh_tokens rt
      WHERE rt.token_hash = SHA2(uc.refresh_token, 256)
  );
