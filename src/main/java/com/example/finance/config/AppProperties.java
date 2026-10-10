package com.example.finance.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Application-specific settings bound from the {@code app.*} namespace.
 *
 * @param timeZone       zone that defines "today" for business date rules
 * @param maxRequestSize largest accepted request body; larger requests get HTTP 413
 * @param security       security toggles
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("UTC") ZoneId timeZone, @DefaultValue("16KB") DataSize maxRequestSize,
                            @DefaultValue Security security) {

    /**
     * @param csrfEnabled whether CSRF tokens are required on state-changing requests
     */
    public record Security(@DefaultValue("false") boolean csrfEnabled) {
    }
}
