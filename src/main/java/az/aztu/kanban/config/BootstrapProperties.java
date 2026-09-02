package az.aztu.kanban.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator accounts that are created on start-up when they do not exist yet.
 * Entries with a blank e-mail or password are ignored, which lets an installation
 * leave the optional slots empty.
 */
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
@Getter
@Setter
public class BootstrapProperties {

    private List<AdminAccount> admins = new ArrayList<>();

    public List<AdminAccount> usableAdmins() {
        return admins.stream().filter(AdminAccount::isUsable).toList();
    }

    @Getter
    @Setter
    public static class AdminAccount {

        private String email;
        private String password;
        private String fullName;
        private String title;
        private String department;

        public boolean isUsable() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        public String normalizedEmail() {
            return email.trim().toLowerCase();
        }

        public String displayName() {
            return (fullName == null || fullName.isBlank()) ? normalizedEmail() : fullName.trim();
        }
    }
}
