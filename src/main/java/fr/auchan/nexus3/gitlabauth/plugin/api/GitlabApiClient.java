package fr.auchan.nexus3.gitlabauth.plugin.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import fr.auchan.nexus3.gitlabauth.plugin.GitlabAuthenticationException;
import fr.auchan.nexus3.gitlabauth.plugin.GitlabPrincipal;
import fr.auchan.nexus3.gitlabauth.plugin.config.GitlabAuthConfiguration;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.Pagination;
import org.gitlab.api.models.GitlabGroup;
import org.gitlab.api.models.GitlabUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.security.Roles;
import org.sonatype.nexus.security.SecurityApi;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@Named("GitlabApiClient")
public class GitlabApiClient {
    private static final Logger LOG = LoggerFactory.getLogger(GitlabApiClient.class);

    private GitlabAPI client;
    private GitlabAuthConfiguration configuration;
    private SecurityApi securityApi;
    // Cache token lookups to reduce the load on Github's User API to prevent hitting the rate limit.
    private Cache<String, GitlabPrincipal> tokenToPrincipalCache;

    public GitlabApiClient() {
        LOG.debug("GitlabApiClient() called ");
        init();
    }

    public GitlabApiClient(GitlabAPI client, GitlabAuthConfiguration configuration) {
        LOG.debug("GitlabApiClient() called  with: client = {}, configuration = {}", client, configuration);
        this.client = client;
        this.configuration = configuration;
        initPrincipalCache();
    }

    @Inject
    public GitlabApiClient(GitlabAuthConfiguration configuration, SecurityApi securityApi) {
        LOG.debug("GitlabApiClient() called  with: configuration = {}", configuration);
        this.configuration = configuration;
        this.securityApi = securityApi;
        init();
    }

    private void init() {
        LOG.debug("init() called ");
        client = GitlabAPI.connect(configuration.getGitlabApiUrl(), configuration.getGitlabApiKey());
        initPrincipalCache();
    }

    private void initPrincipalCache() {
        LOG.debug("initPrincipalCache() called ");
        tokenToPrincipalCache = CacheBuilder.newBuilder()
                .expireAfterWrite(configuration.getPrincipalCacheTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public GitlabPrincipal authz(String login, char[] token) throws GitlabAuthenticationException {
        LOG.debug("authz() called  with: login = {}, token = {}", login, token);
        String cacheKey = login + "|" + new String(token);
        GitlabPrincipal cached = tokenToPrincipalCache.getIfPresent(cacheKey);
        if (cached != null) {
            LOG.debug("Using cached principal for login: {}", login);
            LOG.debug("authz() returned: {}", cached);
            return cached;
        } else {
            GitlabPrincipal principal = doAuthz(getGitlabUser(login, token));
            tokenToPrincipalCache.put(cacheKey, principal);
            LOG.debug("authz() returned: {}", principal);
            return principal;
        }
    }

    private GitlabUser getGitlabUser(String loginName, char[] token) throws GitlabAuthenticationException {
        LOG.debug("getGitlabUser() called  with: loginName = {}, token = {}", loginName, token);
        GitlabUser gitlabUser;
        try {
            GitlabAPI gitlabAPI = GitlabAPI.connect(configuration.getGitlabApiUrl(), String.valueOf(token));
            gitlabUser = gitlabAPI.getUser();
        } catch (Exception e) {
            throw new GitlabAuthenticationException(e);
        }

        if (gitlabUser == null || !loginName.equals(gitlabUser.getEmail())) {
            throw new GitlabAuthenticationException("Given username not found or does not match Github Username!");
        }
        return gitlabUser;
    }

    private GitlabPrincipal doAuthz(GitlabUser gitlabUser) throws GitlabAuthenticationException {
        LOG.debug("doAuthz() called  with: gitlabUser = {}", gitlabUser);

        GitlabPrincipal principal = new GitlabPrincipal();
        principal.setUsername(gitlabUser.getEmail());

        Set<String> assignableRoles = getAssignableRoles(gitlabUser);
        if (!assignableRoles.isEmpty()) {
            principal.setGroups(assignableRoles);
        }

        if (securityApi != null) {
            createNexusUser(gitlabUser);
        }

        LOG.debug("doAuthz() returned: {}", principal);
        return principal;
    }

    private void createNexusUser(GitlabUser gitlabUser) {

    }

    private Set<String> getAssignableRoles(GitlabUser gitlabUser) throws GitlabAuthenticationException {
        Set<String> roles = new HashSet<>();
        if (gitlabUser.isAdmin() && configuration.isGitlabAdminMappingEnabled()) {
            roles.add(Roles.ADMIN_ROLE_ID);
        }
        if (configuration.getGitlabDefaultRole() != null && !configuration.getGitlabDefaultRole().isEmpty()) {
            roles.add(configuration.getGitlabDefaultRole());
        } else {
            roles.addAll(getGroups((gitlabUser.getUsername())));
        }
        return roles;
    }

    private Set<String> getGroups(String username) throws GitlabAuthenticationException {
        LOG.debug("getGroups() called  with: username = {}", username);
        List<GitlabGroup> groups;
        try {
            groups = client.getGroupsViaSudo(username, new Pagination().withPerPage(Pagination.MAX_ITEMS_PER_PAGE));
        } catch (IOException e) {
            throw new GitlabAuthenticationException("Could not fetch groups for given username");
        }
        return groups.stream().map(this::mapGitlabGroupToNexusRole).collect(Collectors.toSet());
    }

    private String mapGitlabGroupToNexusRole(GitlabGroup team) {
        LOG.debug("mapGitlabGroupToNexusRole() called  with: team = {}", team);
        return team.getPath();
    }
}