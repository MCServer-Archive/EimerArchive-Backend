package com.mcserverarchive.archive.controller;

import com.mcserverarchive.archive.dtos.out.ErrorDto;
import com.mcserverarchive.archive.model.Account;
import com.mcserverarchive.archive.model.ERole;
import com.mcserverarchive.archive.model.Role;
import com.mcserverarchive.archive.model.Token;
import com.mcserverarchive.archive.payload.request.LoginRequest;
import com.mcserverarchive.archive.payload.request.SignupRequest;
import com.mcserverarchive.archive.payload.response.MessageResponse;
import com.mcserverarchive.archive.repositories.AccountRepository;
import com.mcserverarchive.archive.repositories.RoleRepository;
import com.mcserverarchive.archive.repositories.TokenRepository;
import com.mcserverarchive.archive.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@RestController()
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final TokenRepository tokenRepository;

    private final AccountRepository accountRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder encoder;

    @PostMapping("signout")
    public ResponseEntity<?> logoutUser(@CookieValue(name = "user-cookie") String ct) {

        ResponseCookie cookie = ResponseCookie.from("user-cookie", "").path("/").httpOnly(false).maxAge(0).sameSite("None").secure(true).domain("").build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new MessageResponse("You've been signed out!"));
    }

    @PostMapping("signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Optional<Account> optionalAccount = this.accountRepository.findByUsernameEquals(loginRequest.getUsername());

        if (optionalAccount.isEmpty()) {
            return ResponseEntity.ok().body(ErrorDto.create(0, "Invalid username"));
        }

        if (!BCrypt.checkpw(loginRequest.getPassword(), optionalAccount.get().getPassword())) {
            return ResponseEntity.ok().body("{\"errorText\": \"Incorrect password\"}");
        }

        Token token = new Token(LocalDateTime.now(), LocalDateTime.now().plusWeeks(1), "0.0.0.0", optionalAccount.get());
        accountService.createToken(token);

        ResponseCookie cookie = ResponseCookie.from("user-cookie", token.getToken()).path("/").secure(true).httpOnly(false).maxAge(604800).domain("").build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @PostMapping("signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (accountRepository.existsByUsernameEqualsIgnoreCase(signUpRequest.getUsername())) {
            return ResponseEntity.ok().body(ErrorDto.create(0, "Username is already taken!"));
        }

        if (accountRepository.existsByEmailEqualsIgnoreCase(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(ErrorDto.create(0, "Email is already in use!"));
        }

        // Create new user's account
        Account account = new Account(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = new HashSet<>(Collections.singletonList("USER"));
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin" -> {
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);
                    }
                    case "mod" -> {
                        Role modRole = roleRepository.findByName(ERole.ROLE_MODERATOR)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(modRole);
                    }
                    default -> {
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                    }
                }
            });
        }

        //account.setRoles(roles);
        accountRepository.save(account);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/auth/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedOriginPatterns("http://localhost:3000")
                        .allowCredentials(true)
                        .allowedMethods("GET", "POST");
            }
        };
    }
}