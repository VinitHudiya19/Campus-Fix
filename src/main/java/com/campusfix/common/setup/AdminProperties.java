package com.campusfix.common.setup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campusfix.admin")
public record AdminProperties(String email, String password) {
}
