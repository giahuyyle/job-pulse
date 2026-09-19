package com.huy.jobpulse.discovery.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class LocalAdminGuard {

    public void requireLocal(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr())
                    .isLoopbackAddress()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Administration is restricted to localhost"
                );
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Could not validate admin client address",
                    exception
            );
        }
    }
}
