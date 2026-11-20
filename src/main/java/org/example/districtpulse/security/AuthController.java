package org.example.districtpulse.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtEncoder encoder;
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>();
    private record Attempt(int count,Instant start){}
    public AuthController(UserRepository users,PasswordEncoder passwords,JwtEncoder encoder){this.users=users;this.passwords=passwords;this.encoder=encoder;}
    public record Login(@Email @NotBlank @Size(max=254) String email,@NotBlank @Size(max=72) String password){}
    public static Map<String,Object> profile(AppUser u){
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",u.getId());m.put("name",u.getName());m.put("email",u.getEmail());
        m.put("role",u.getRole());m.put("designation",u.getDesignation());if(u.getDepartmentId()!=null)m.put("departmentId",u.getDepartmentId());return m;
    }
    @PostMapping("/login") public synchronized Map<String,Object> login(@Valid @RequestBody Login input) {
        String email=input.email().trim().toLowerCase(Locale.ROOT);Instant now=Instant.now();
        attempts.entrySet().removeIf(e->e.getValue().start().plusSeconds(900).isBefore(now));
        Attempt a=attempts.getOrDefault(email,new Attempt(0,now));
        if(a.count()>=10 || attempts.size()>10000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please retry later");
        AppUser u=users.findByEmail(email).orElse(null);
        if(u==null || !u.isEnabled() || !passwords.matches(input.password(),u.getPasswordHash())){
            attempts.put(email,new Attempt(a.count()+1,a.start()));throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        }
        attempts.remove(email);Instant expiry=now.plusSeconds(3600);
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("districtpulse").subject(u.getId()).issuedAt(now).expiresAt(expiry).claim("role",u.getRole()).claim("version",u.getTokenVersion()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return Map.of("token",token,"user",profile(u),"expiresAt",expiry.toString());
    }
    @GetMapping("/me") public Map<String,Object> me(Authentication a){return profile(users.findById(a.getName()).orElseThrow());}
    @PostMapping("/logout") public Map<String,String> logout(){return Map.of("message","Discard the access token. Access expires after one hour.");}
    @PostMapping("/forgot-password") public Map<String,String> reset(){
        return Map.of("message","Contact your platform administrator for a password reset. Email delivery is not configured.");
    }
    public record NewUser(@Email @NotBlank @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(max=200) String name,@NotBlank String role,String departmentId){}
    @PostMapping("/users") @PreAuthorize("hasRole('administrator')")
    public Map<String,Object> create(@Valid @RequestBody NewUser input){
        if(!Set.of("collector","administrator","department_officer").contains(input.role()))throw new IllegalArgumentException("Invalid role");
        if("department_officer".equals(input.role()) && !org.example.districtpulse.platform.ReferenceData.DEPARTMENTS.containsKey(input.departmentId()==null?"":input.departmentId()))throw new IllegalArgumentException("Department required");
        AppUser u=new AppUser();u.setId(UUID.randomUUID().toString());u.setEmail(input.email().toLowerCase(Locale.ROOT));u.setPasswordHash(passwords.encode(input.password()));
        u.setName(input.name());u.setRole(input.role());u.setDesignation(input.role());u.setDepartmentId(input.departmentId());return profile(users.save(u));
    }
    @GetMapping("/users") @PreAuthorize("hasRole('administrator')") public List<Map<String,Object>> list(){return users.findAll().stream().map(AuthController::profile).toList();}
    @PatchMapping("/users/{id}") @PreAuthorize("hasRole('administrator')")
    public Map<String,Object> manage(@PathVariable String id,@RequestBody Map<String,Object> body,Authentication auth){
        AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
        if(body.containsKey("password")){if(!(body.get("password") instanceof String password)||password.length()<12||password.length()>72)throw new IllegalArgumentException("Password must contain 12-72 characters");u.setPasswordHash(passwords.encode(password));u.setTokenVersion(u.getTokenVersion()+1);}
        if(body.containsKey("enabled")){if(!(body.get("enabled") instanceof Boolean enabled))throw new IllegalArgumentException("Invalid account status");if(!enabled&&id.equals(auth.getName()))throw new IllegalArgumentException("Cannot disable your current account");u.setEnabled(enabled);}
        users.save(u);return profile(u);
    }
    @Bean ApplicationRunner bootstrap(@Value("${app.admin-email}") String email,@Value("${app.admin-password}") String password){
        return args->{if(users.count()==0){
            if(email.isBlank()||password.length()<12||password.length()>72)throw new IllegalStateException("First start requires ADMIN_EMAIL and ADMIN_PASSWORD (12-72 characters)");
            AppUser u=new AppUser();u.setId(UUID.randomUUID().toString());u.setEmail(email.trim().toLowerCase(Locale.ROOT));u.setName("Platform Administrator");u.setRole("administrator");
            u.setDesignation("Platform Administrator");u.setPasswordHash(passwords.encode(password));users.save(u);
        }};
    }
}
