package az.aztu.kanban.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.MEMBER;

    @Column(length = 120)
    private String title;

    @Column(length = 120)
    private String department;

    @Column(length = 30)
    private String phone;

    @Column(name = "avatar_color", length = 20)
    private String avatarColor = "#6366f1";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Two User objects are the same person when they carry the same id.
     *
     * Without this a Set<User> falls back to object identity, and the same person loaded
     * twice - once detached from the JWT filter, once managed inside the request
     * transaction - counts as two members. That is how board_member ended up with two
     * rows for one (board_id, user_id).
     *
     * instanceof rather than getClass() so a Hibernate proxy still compares equal, and a
     * constant hashCode so an entity whose id is assigned after insert does not move
     * bucket inside a set it is already in.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id != null && id.equals(user.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }

    public String initials() {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}
