# GENERATE: src/main/java/com/codrite/ruleaudit/audit/AuditKey.java

Deterministic audit id per the contract. Package com.codrite.ruleaudit.audit.
- public final class AuditKey with a private constructor (utility class).
- public static String of(String topic, int partition, long offset):
    - String input = topic + "|" + partition + "|" + offset;
    - Compute SHA-256 of input.getBytes(StandardCharsets.UTF_8) via
      MessageDigest.getInstance("SHA-256").
    - Return the digest as a lowercase hex string.
    - Wrap NoSuchAlgorithmException in an IllegalStateException (SHA-256 always present).
- Output exactly one top-level class.
