package ru.telemetry.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.telemetry.query.model.User;
import ru.telemetry.query.repository.UserRepository;
import ru.telemetry.query.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void login_correctPassword_returnsToken() {
        User user = new User(1L, "admin", "hashed", "ADMIN");
        given(userRepository.findByUsername("admin")).willReturn(Mono.just(user));
        given(passwordEncoder.matches("secret", "hashed")).willReturn(true);
        given(jwtService.issue("admin", "ADMIN")).willReturn("jwt-token");

        StepVerifier.create(controller.login(new AuthController.LoginRequest("admin", "secret")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().token()).isEqualTo("jwt-token");
                    assertThat(response.getBody().role()).isEqualTo("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        User user = new User(1L, "admin", "hashed", "ADMIN");
        given(userRepository.findByUsername("admin")).willReturn(Mono.just(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        StepVerifier.create(controller.login(new AuthController.LoginRequest("admin", "wrong")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .verifyComplete();
    }

    @Test
    void login_unknownUser_returnsUnauthorized() {
        given(userRepository.findByUsername("nobody")).willReturn(Mono.empty());

        StepVerifier.create(controller.login(new AuthController.LoginRequest("nobody", "secret")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .verifyComplete();
    }

    @Test
    void login_blankCredentials_returnsUnauthorizedWithoutHittingDb() {
        StepVerifier.create(controller.login(new AuthController.LoginRequest("", "")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .verifyComplete();

        verifyNoInteractions(userRepository, jwtService);
    }
}
