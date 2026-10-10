package com.example.finance.entity;

import com.example.finance.entity.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A transaction category. Default categories have no owner and are shared by all users; custom categories
 * belong to exactly one user. Categories are immutable once created: they can only be created or deleted.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 50, updatable = false)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10, updatable = false)
    private TransactionType type;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean defaultCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", updatable = false)
    private User owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
        // for JPA
    }

    private Category(String name, TransactionType type, User owner, boolean defaultCategory) {
        this.name = name;
        this.normalizedName = normalize(name);
        this.type = type;
        this.owner = owner;
        this.defaultCategory = defaultCategory;
    }

    /**
     * Creates a custom category owned by the given user.
     *
     * @param name  display name (already trimmed)
     * @param type  income or expense
     * @param owner owning user
     * @return a new, not yet persisted category
     */
    public static Category custom(String name, TransactionType type, User owner) {
        return new Category(name, type, owner, false);
    }

    /**
     * Canonical form used for case-insensitive name comparison and uniqueness.
     *
     * @param name raw category name
     * @return trimmed, lower-cased name
     */
    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public TransactionType getType() {
        return type;
    }

    public boolean isDefaultCategory() {
        return defaultCategory;
    }

    public boolean isCustom() {
        return !defaultCategory;
    }

    public User getOwner() {
        return owner;
    }
}
