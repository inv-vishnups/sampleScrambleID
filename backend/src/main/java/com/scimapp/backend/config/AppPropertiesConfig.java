package com.scimapp.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.scimapp.backend.security.JwtProperties;
import com.scimapp.backend.security.ScimAuditProperties;
import com.scimapp.backend.security.ScimAuthProperties;

@Configuration
@EnableConfigurationProperties({ JwtProperties.class, ScimAuthProperties.class, ScimAuditProperties.class })
public class AppPropertiesConfig {
}
