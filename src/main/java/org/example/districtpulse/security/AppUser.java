package org.example.districtpulse.security;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="app_users") @Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id private String id;
    @Column(unique=true,nullable=false) private String email;
    @Column(nullable=false) private String passwordHash;
    private String name;
    private String role;
    private String designation;
    private String departmentId;
    private boolean enabled=true;
    private long tokenVersion;
}
