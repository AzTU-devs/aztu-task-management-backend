package az.aztu.kanban;

import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.AuthDtos.LoginRequest;
import az.aztu.kanban.dto.AuthDtos.LoginResponse;
import az.aztu.kanban.config.DataSeeder;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the multi-account bootstrap: every configured slot becomes an administrator,
 * blank and duplicate slots are ignored, and the credentials actually work at login.
 */
@SpringBootTest
@ActiveProfiles("test")
class BootstrapAdminTest {

    @Autowired UserRepository userRepository;
    @Autowired AuthService authService;
    @Autowired DataSeeder dataSeeder;

    @Test
    void everyConfiguredSlotBecomesAnAdministrator() {
        User first = userRepository.findByEmailIgnoreCase("admin@aztu.edu.az").orElseThrow();
        User second = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();

        assertThat(first.getRole()).isEqualTo(Role.ADMIN);
        assertThat(second.getRole()).isEqualTo(Role.ADMIN);
        assertThat(second.isActive()).isTrue();
        assertThat(second.getFullName()).isEqualTo("Second Administrator");
    }

    @Test
    void addressesAreStoredLowercaseSoLoginIsCaseInsensitive() {
        User second = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();
        assertThat(second.getEmail()).isEqualTo("second.admin@aztu.edu.az");

        LoginResponse response = authService.login(new LoginRequest("SECOND.ADMIN@AZTU.EDU.AZ", "Sec0nd!&pass"));
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void passwordsWithShellPunctuationSurviveIntact() {
        LoginResponse response = authService.login(new LoginRequest("second.admin@aztu.edu.az", "Sec0nd!&pass"));
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void duplicateSlotsAreIgnoredRatherThanOverwritingTheFirst() {
        // the third slot repeats the first address with a different password: it must not win
        LoginResponse response = authService.login(new LoginRequest("admin@aztu.edu.az", "Admin123!"));
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@aztu.edu.az", "DifferentPassword1!")))
                .isInstanceOf(BadCredentialsException.class);
    }

    /**
     * An administrator who is offboarded through the UI must stay offboarded. Re-enabling
     * them on the next container restart would silently restore full access while their
     * old password still worked.
     */
    @Test
    void aDeactivatedAdministratorIsNotRevivedByARestart() {
        User admin = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();
        admin.setActive(false);
        admin.setRole(Role.MEMBER);
        userRepository.save(admin);

        try {
            dataSeeder.run();   // exactly what happens on the next boot

            User after = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();
            assertThat(after.isActive()).isFalse();
            assertThat(after.getRole()).isEqualTo(Role.MEMBER);

            assertThatThrownBy(() -> authService.login(new LoginRequest("second.admin@aztu.edu.az", "Sec0nd!&pass")))
                    .isInstanceOf(DisabledException.class);
        } finally {
            User restore = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();
            restore.setActive(true);
            restore.setRole(Role.ADMIN);
            userRepository.save(restore);
        }
    }

    /** A still-active non-admin named in a slot is promoted - that part must keep working. */
    @Test
    void anActiveNonAdministratorInASlotIsPromoted() {
        User admin = userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow();
        admin.setRole(Role.MEMBER);
        userRepository.save(admin);

        dataSeeder.run();

        assertThat(userRepository.findByEmailIgnoreCase("second.admin@aztu.edu.az").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    /** No password that lives in tracked source may open a real account. */
    @Test
    void seededDemoPeopleCannotBeSignedInAs() {
        assertThat(userRepository.findByEmailIgnoreCase("nigar.aliyeva@aztu.edu.az")).isPresent();

        for (String email : new String[]{
                "nigar.aliyeva@aztu.edu.az", "elvin.mammadov@aztu.edu.az", "leyla.huseynova@aztu.edu.az"}) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "Aztu2024!")))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void blankSlotsCreateNoAccount() {
        assertThat(userRepository.findAll().stream().map(User::getFullName)).doesNotContain("Unused");
        assertThat(userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .count()).isEqualTo(2);
    }
}
