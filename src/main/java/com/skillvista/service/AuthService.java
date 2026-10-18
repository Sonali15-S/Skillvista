package com.skillvista.service;

import com.skillvista.model.Account;
import com.skillvista.model.AuthToken;
import com.skillvista.repo.AccountRepository;
import com.skillvista.repo.AuthTokenRepository;
import com.skillvista.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final AuthTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accounts, AuthTokenRepository tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    // ---------- password hashing (PBKDF2, built into the JDK) ----------
    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(password, salt));
    }

    public boolean verify(String password, String stored) {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        return MessageDigest.isEqual(expected, pbkdf2(password, salt));
    }

    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- sessions ----------
    public String issueToken(Account account) {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        AuthToken t = new AuthToken();
        t.token = HexFormat.of().formatHex(raw);
        t.gmail = account.gmail;
        tokens.save(t);
        return t.token;
    }

    public void revoke(String token) {
        if (token != null) tokens.findByToken(token).ifPresent(tokens::delete);
    }

    public String tokenOf(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        return req.getParameter("token"); // EventSource cannot set headers
    }

    /** Returns the logged-in account, or throws 401. If role is not null, also checks the role (403). */
    public Account require(HttpServletRequest req, String role) {
        String token = tokenOf(req);
        if (token == null) throw new ApiException(401, "Please log in first.");
        AuthToken t = tokens.findByToken(token).orElseThrow(() -> new ApiException(401, "Your session has expired. Please log in again."));
        Account a = accounts.findByGmailIgnoreCase(t.gmail).orElseThrow(() -> new ApiException(401, "Account no longer exists."));
        if (role != null && !role.equals(a.role)) throw new ApiException(403, "This page is only for " + role + " accounts.");
        return a;
    }
}
