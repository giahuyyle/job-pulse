package com.huy.jobpulse.discovery.application;

public record BoardVerification(boolean valid, String detail) {

    public static BoardVerification verified() {
        return new BoardVerification(true, null);
    }

    public static BoardVerification notFound(String detail) {
        return new BoardVerification(false, detail);
    }
}
