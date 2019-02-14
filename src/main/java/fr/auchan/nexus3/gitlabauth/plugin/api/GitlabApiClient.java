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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@Named("GitlabApiClient")
public class GitlabApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabApiClient.class);

    private GitlabAPI client;
    private GitlabAuthConfiguration configuration;
    // Cache token lookups to reduce the load on Github's User API to prevent hitting the rate limit.
    private Cache<String, GitlabPrincipal> tokenToPrincipalCache;

    public GitlabApiClient() {
        //no args constructor is needed
    }

    public GitlabApiClient(GitlabAPI client, GitlabAuthConfiguration configuration) {
        this.client = client;
        this.configuration = configuration;
        initPrincipalCache();
    }

    @Inject
    public GitlabApiClient(GitlabAuthConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    public void init() {
        client = GitlabAPI.connect(configuration.getGitlabApiUrl(), configuration.getGitlabApiKey());
        initPrincipalCache();
    }

    private void initPrincipalCache() {
        tokenToPrincipalCache = CacheBuilder.newBuilder()
                .expireAfterWrite(configuration.getPrincipalCacheTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public GitlabPrincipal authz(String login, char[] token) throws GitlabAuthenticationException {
        // Combine the login and the token as the cache key since they are both used to generate the principal. If either changes we should obtain a new
        // principal.
        String cacheKey = login + "|" + new String(token);
        GitlabPrincipal cached = tokenToPrincipalCache.getIfPresent(cacheKey);
        if (cached != null) {
            LOGGER.debug("Using cached principal for login: {}", login);
            return cached;
        } else {
            GitlabPrincipal principal = doAuthz(login, token);
            tokenToPrincipalCache.put(cacheKey, principal);
            return principal;
        }
    }

    private GitlabPrincipal doAuthz(String loginName, char[] token) throws GitlabAuthenticationException {
        GitlabUser gitlabUser;
        List<GitlabGroup> groups = null;
        try {
            GitlabAPI gitlabAPI = GitlabAPI.connect(configuration.getGitlabApiUrl(), String.valueOf(token));
            gitlabUser = gitlabAPI.getUser();
        } catch (Exception e) {
            throw new GitlabAuthenticationException(e);
        }

        if (gitlabUser==null || !loginName.equals(gitlabUser.getEmail())) {
            throw new GitlabAuthenticationException("Given username not found or does not match GitLab Username!");
        }

        GitlabPrincipal principal = new GitlabPrincipal();

        principal.setUsername(gitlabUser.getEmail());
        principal.setGroups(getGroups((gitlabUser.getUsername())));

        return principal;
    }

    private Set<String> getGroups(String username) throws GitlabAuthenticationException {
        List<GitlabGroup> groups;
        try {
            groups = client.getGroupsViaSudo(username,new Pagination().withPerPage(Pagination.MAX_ITEMS_PER_PAGE));
        } catch (IOException e) {
            throw new GitlabAuthenticationException("Could not fetch groups for given username");
        }
        return groups.stream().map(this::mapGitlabGroupToNexusRole).collect(Collectors.toSet());
    }

    private String mapGitlabGroupToNexusRole(GitlabGroup team) {
        return team.getPath();
    }


}