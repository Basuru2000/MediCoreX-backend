package com.medicorex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "quarantine")
@Data
public class QuarantineConfig {

    private Auto auto = new Auto();
    private Review review = new Review();
    private Notification notification = new Notification();
    private Disposal disposal = new Disposal();

    @Data
    public static class Auto {
        private boolean enabled = true;
        private String cron = "0 30 2 * * *"; // 2:30 AM daily
        private int batchSize = 100;
        private boolean includeExpired = true;
        private boolean includeDefective = true;
    }

    @Data
    public static class Review {
        private int timeoutDays = 7;
        private boolean escalationEnabled = true;
        private int escalationDays = 3;
        private String escalationRole = "HOSPITAL_MANAGER";
    }

    @Data
    public static class Notification {
        private boolean enabled = true;
        private boolean emailEnabled = false;
        private boolean smsEnabled = false;
        private String[] notifyRoles = {"HOSPITAL_MANAGER", "PHARMACY_STAFF"};
    }

    @Data
    public static class Disposal {
        private String[] methods = {"Incineration", "Chemical Treatment", "Return to Supplier", "Donation", "Other"};
        private boolean certificateRequired = true;
        private boolean photoRequired = false;
        private String documentPath = "/documents/quarantine/disposal/";
    }
}