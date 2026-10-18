package com.jobtracker.security;

/** The authenticated caller, extracted from the JWT. Available via {@code @AuthenticationPrincipal}. */
public record CurrentUser(Long id, String email) {}
