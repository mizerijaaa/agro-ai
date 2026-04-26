package com.uiktp.agro.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String fullName = "Земјоделец";
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String role = "FARMER";

    /** Јавна дејност (пример: Земјоделец). Стари редови: null → третирај како „Земјоделец“. */
    @Column(length = 200)
    private String occupation;

    /**
     * Слика за аватар: data URL (data:image/...) или null.
     * Не ја вчитуваме во JWT; се чува само во профил-API.
     */
    @Lob
    @Column
    private String avatarData;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public String getAvatarData() { return avatarData; }
    public void setAvatarData(String avatarData) { this.avatarData = avatarData; }
}
