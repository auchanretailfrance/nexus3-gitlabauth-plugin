package fr.auchan.nexus3.gitlabauth.plugin.config;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

@Singleton
@Named
public class GitlabAuthConfiguration {
    private static final String CONFIG_FILE = "gitlabauth.properties";

    private static final Duration DEFAULT_PRINCIPAL_CACHE_TTL = Duration.ofMinutes(1);

    private static final String DEFAULT_GITLAB_URL = "https://gitlab.com";

    private static final String GITLAB_API_URL_KEY = "gitlab.api.url";

    private static final String GITLAB_SUDO_API_KEY_KEY = "gitlab.api.key";

    private static final String GITLAB_PRINCIPAL_CACHE_TTL_KEY = "github.principal.cache.ttl";

    private static final String GITLAB_DEFAULT_ROLE = "gitlab.role.default";

    private static final String GITLAB_ADMIN_MAPPING = "gitlab.role.admin.mapping.enabled";

    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabAuthConfiguration.class);

    private Properties configuration;

    public GitlabAuthConfiguration() {
        configuration = new Properties();

        try {
            configuration.load(Files.newInputStream(Paths.get(".", "etc", CONFIG_FILE)));
        } catch (IOException e) {
            LOGGER.warn("Error reading github oauth properties, falling back to default configuration", e);
        }
    }

    public String getGitlabApiUrl() {
        return configuration.getProperty(GITLAB_API_URL_KEY, DEFAULT_GITLAB_URL);
    }

    public String getGitlabApiKey() {
        return configuration.getProperty(GITLAB_SUDO_API_KEY_KEY);
    }

    public Duration getPrincipalCacheTtl() {
        return Duration.parse(configuration.getProperty(GITLAB_PRINCIPAL_CACHE_TTL_KEY, DEFAULT_PRINCIPAL_CACHE_TTL.toString()));
    }

    public String getGitlabDefaultRole() {
        return configuration.getProperty(GITLAB_DEFAULT_ROLE);
    }

    public boolean isGitlabAdminMappingEnabled() {
        return configuration.getProperty(GITLAB_ADMIN_MAPPING).equals("true");
    }
}