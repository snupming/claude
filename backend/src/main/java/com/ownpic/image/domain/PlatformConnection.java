package com.ownpic.image.domain;

import com.ownpic.auth.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "platform_connections")
public class PlatformConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformType platform;

    @Column(name = "store_name", length = 200)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.CONNECTED;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "channel_no")
    private Long channelNo;

    @Column(name = "store_url", length = 500)
    private String storeUrl;

    @Column(name = "solution_id", length = 100)
    private String solutionId;

    @Column(name = "subscription_id", length = 100)
    private String subscriptionId;

    @Column(name = "plan_id", length = 100)
    private String planId;

    @Column(name = "subscription_status", length = 50)
    private String subscriptionStatus;

    @Column(name = "business_registration_number", length = 50)
    private String businessRegistrationNumber;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters & Setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public PlatformType getPlatform() { return platform; }
    public void setPlatform(PlatformType platform) { this.platform = platform; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public ConnectionStatus getStatus() { return status; }
    public void setStatus(ConnectionStatus status) { this.status = status; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Long getChannelNo() { return channelNo; }
    public void setChannelNo(Long channelNo) { this.channelNo = channelNo; }

    public String getStoreUrl() { return storeUrl; }
    public void setStoreUrl(String storeUrl) { this.storeUrl = storeUrl; }

    public String getSolutionId() { return solutionId; }
    public void setSolutionId(String solutionId) { this.solutionId = solutionId; }

    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public String getBusinessRegistrationNumber() { return businessRegistrationNumber; }
    public void setBusinessRegistrationNumber(String businessRegistrationNumber) { this.businessRegistrationNumber = businessRegistrationNumber; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
