package com.syncinator.kodi.login.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Proof Key for Code Exchange (RFC 7636), S256 method. */
public final class Pkce {
	private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

	private Pkce() {
	}

	/** 32 random bytes, base64url: 43 characters, within the 43-128 allowed by the RFC. */
	public static String verifier(final SecureRandom random) {
		final byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return BASE64URL.encodeToString(bytes);
	}

	public static String challenge(final String verifier) {
		try {
			return BASE64URL.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
