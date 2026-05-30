package com.study.backend.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

	private final boolean cookieSecure;

	public AuthCookieFactory(@Value("${app.cookie.secure}") boolean cookieSecure) {
		this.cookieSecure = cookieSecure;
	}

	public ResponseCookie createLoginTokenCookie(String token) {
		return baseCookie("token", token).build();
	}

	public ResponseCookie expireLoginTokenCookie() {
		return baseCookie("token", "").maxAge(0).build();
	}

	public ResponseCookie createSecretTokenCookie(String token) {
		return baseCookie("secret_token", token)
			.maxAge(600)
			.build();
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(cookieSecure)
			.sameSite("Strict")
			.path("/");
	}
}
