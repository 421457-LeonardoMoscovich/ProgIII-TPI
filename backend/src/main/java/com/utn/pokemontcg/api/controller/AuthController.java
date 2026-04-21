package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.AuthRequest;
import com.utn.pokemontcg.api.dto.AuthResponse;
import com.utn.pokemontcg.config.JwtUtil;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registro e inicio de sesión")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    @Operation(summary = "Registra un nuevo usuario", description = "Crea cuenta y devuelve JWT. Falla con 409 si username o email ya existen.")
    public AuthResponse register(@RequestBody AuthRequest req) {
        if (userRepo.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ya existe");
        }
        if (req.email() != null && userRepo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya existe");
        }
        var user = new User(req.username(), req.email(), encoder.encode(req.password()));
        userRepo.save(user);
        return new AuthResponse(jwtUtil.generateToken(req.username()), req.username());
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión", description = "Devuelve JWT si las credenciales son correctas. Solo requiere username y password.")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        return userRepo.findByUsername(req.username())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .map(u -> ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(u.getUsername()), u.getUsername())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
