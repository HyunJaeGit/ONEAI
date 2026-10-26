package local.oneai.config;

import java.nio.file.*;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class LocalConfig implements WebMvcConfigurer {
    public static Path root() {
        String override = System.getProperty("oneai.root");
        if (override != null) {
            Path configured = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configured.resolve("AGENTS.md"))) throw new IllegalStateException("Invalid oneai.root");
            return configured;
        }
        try {
            Path candidate = new org.springframework.boot.system.ApplicationHome(LocalConfig.class).getDir().toPath();
            while (candidate != null) {
                if (Files.isRegularFile(candidate.resolve("AGENTS.md"))) return candidate;
                candidate = candidate.getParent();
            }
        } catch (Exception ignored) { }
        throw new IllegalStateException("Cannot locate repository; set absolute oneai.root");
    }
    public static Path databasePath(Environment env) {
        Path configured = Path.of(env.getRequiredProperty("oneai.db"));
        return (configured.isAbsolute() ? configured : root().resolve(configured)).toAbsolutePath().normalize();
    }
    @Bean
    DataSource dataSource(Environment env) throws Exception {
        Path file = databasePath(env);
        Files.createDirectories(file.getParent());
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(2000);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + file);
        return source;
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = root();
        registry.addResourceHandler("/assets/**").addResourceLocations(root.resolve("assets").toUri().toString());
        registry.addResourceHandler("/pages/**").addResourceLocations(root.resolve("pages").toUri().toString());
    }
}
