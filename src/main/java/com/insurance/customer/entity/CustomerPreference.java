package com.insurance.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "customer_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerPreference {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Builder.Default @Column(name = "email_alerts") private boolean emailAlerts = true;
    @Builder.Default @Column(name = "sms_alerts") private boolean smsAlerts = true;
    @Builder.Default @Column(name = "push_alerts") private boolean pushAlerts = true;
    @Builder.Default @Column(name = "preferred_lang") private String preferredLang = "en";
    @Builder.Default @Column(name = "renewal_reminder_days") private int renewalReminderDays = 30;
    @Column(name = "updated_at") @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
