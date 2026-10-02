package com.safari.tms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "safari")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Seed seed = new Seed();
    private Permits permits = new Permits();
    private Payments payments = new Payments();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMinutes = 480;
        private String issuer = "safari-tms";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Permits {
        private int expiryWarningDays = 30;
    }

    @Getter
    @Setter
    public static class Payments {
        private int successRate = 80;
        private int declineRate = 15;
        private int timeoutRate = 5;
        private String forceOutcome = "";
    }
}
