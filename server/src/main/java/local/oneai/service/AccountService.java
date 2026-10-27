package local.oneai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.sql.DataSource;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class AccountService {
    public record Account(String accountId, String loginId, String memberType) { }
    public static class AccountException extends RuntimeException {
        public final int status;
        public final String code;
        public AccountException(int status, String code) { super(code); this.status=status; this.code=code; }
    }
    private record Attempt(int failures, Instant windowStart, Instant blockedUntil) { }

    private static final int ITERATIONS = 310_000;
    private static final int KEY_BITS = 256;
    private static final String ADMIN_PIN = "1111";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final String dummyHash = hashPassword(UUID.randomUUID().toString());
    private final Map<String,Attempt> attempts = new ConcurrentHashMap<>();

    public AccountService(DataSource source) {
        this.db = new JdbcTemplate(source);
    }

    /** Ensures the local fixed administrator account uses its configured four-digit PIN. */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAdministrator() {
        var existing = db.query("SELECT member_type,password_hash,is_active FROM user_accounts WHERE login_id=? COLLATE NOCASE",
            (rs, row) -> new Object[]{rs.getString(1),rs.getString(2),rs.getInt(3)}, "admin");
        if (!existing.isEmpty()) {
            Object[] admin = existing.getFirst();
            if (!"ADMIN".equals(admin[0])) throw new IllegalStateException("Reserved administrator login is already assigned");
            if (!verifyPassword(ADMIN_PIN,(String)admin[1]) || ((Integer)admin[2]) != 1)
                db.update("UPDATE user_accounts SET password_hash=?,updated_at=?,is_active=1 WHERE login_id=? COLLATE NOCASE",
                    hashPassword(ADMIN_PIN),Instant.now().toString(),"admin");
            return;
        }
        String now = Instant.now().toString();
        db.update("INSERT INTO user_accounts(account_id,login_id,password_hash,member_type,created_at,updated_at,is_active) VALUES(?,?,?,?,?,?,1)",
            UUID.randomUUID().toString(), "admin", hashPassword(ADMIN_PIN), "ADMIN", now, now);
    }

    public Account register(String loginId, String password) {
        String normalized = normalizeLogin(loginId);
        if (normalized.equals("admin")) throw new AccountException(409, "LOGIN_ID_UNAVAILABLE");
        if (password == null || !password.matches("[0-9]{4}"))
            throw new AccountException(400, "INVALID_PIN");
        String now = Instant.now().toString();
        try {
            db.update("INSERT INTO user_accounts(account_id,login_id,password_hash,member_type,created_at,updated_at,is_active) VALUES(?,?,?,?,?,?,1)",
                UUID.randomUUID().toString(), normalized, hashPassword(password), "USER", now, now);
        } catch (DataIntegrityViolationException ex) {
            throw new AccountException(409, "LOGIN_ID_UNAVAILABLE");
        }
        return new Account(findId(normalized), normalized, "USER");
    }

    public Account authenticate(String loginId, String password, String remoteAddress) {
        String normalized;
        try { normalized = normalizeLogin(loginId); }
        catch (AccountException ex) { normalized = "invalid"; }
        String attemptKey = (remoteAddress == null ? "unknown" : remoteAddress) + "|" + normalized;
        Instant now = Instant.now();
        Attempt prior = attempts.get(attemptKey);
        if (prior != null && prior.blockedUntil() != null && now.isBefore(prior.blockedUntil()))
            throw new AccountException(429, "LOGIN_TEMPORARILY_BLOCKED");

        var rows = db.query("SELECT account_id,login_id,password_hash,member_type,is_active FROM user_accounts WHERE login_id=? COLLATE NOCASE",
            (rs, row) -> new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)}, normalized);
        Object[] row = rows.isEmpty() ? null : rows.getFirst();
        String storedHash = row == null ? dummyHash : (String)row[2];
        boolean valid = password != null && password.matches("[0-9]{4}") && verifyPassword(password, storedHash);
        if (!valid || row == null || ((Integer)row[4]) != 1) {
            failedAttempt(attemptKey, prior, now);
            throw new AccountException(401, "INVALID_CREDENTIALS");
        }
        attempts.remove(attemptKey);
        return new Account((String)row[0], (String)row[1], (String)row[3]);
    }

    public Optional<Account> findActive(String accountId) {
        if (accountId == null || accountId.length() > 64) return Optional.empty();
        var rows = db.query("SELECT account_id,login_id,member_type FROM user_accounts WHERE account_id=? AND is_active=1",
            (rs, row) -> new Account(rs.getString(1),rs.getString(2),rs.getString(3)), accountId);
        return rows.stream().findFirst();
    }

    private synchronized void failedAttempt(String key, Attempt prior, Instant now) {
        Instant start = prior == null || prior.windowStart().plusSeconds(900).isBefore(now) ? now : prior.windowStart();
        int count = prior == null || !start.equals(prior.windowStart()) ? 1 : prior.failures()+1;
        Instant blocked = count >= 5 ? now.plusSeconds(900) : null;
        attempts.put(key, new Attempt(count,start,blocked));
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(entry -> entry.getValue().windowStart().plusSeconds(900).isBefore(now));
    }

    private String findId(String loginId) {
        return db.queryForObject("SELECT account_id FROM user_accounts WHERE login_id=?", String.class, loginId);
    }

    private String normalizeLogin(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{3,32}")) throw new AccountException(400, "INVALID_LOGIN_ID");
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(String password, String encoded) {
        try {
            String[] fields = encoded.split("\\$", -1);
            if (fields.length != 4 || !fields[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(fields[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] expected = Base64.getDecoder().decode(fields[3]);
            return MessageDigest.isEqual(expected, derive(password.toCharArray(), salt, iterations));
        } catch (RuntimeException ex) { return false; }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception ex) { throw new IllegalStateException("Password hashing is unavailable", ex); }
        finally { spec.clearPassword(); java.util.Arrays.fill(password, '\0'); }
    }
}
