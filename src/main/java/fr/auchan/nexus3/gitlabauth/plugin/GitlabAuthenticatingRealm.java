package fr.auchan.nexus3.gitlabauth.plugin;

import fr.auchan.nexus3.gitlabauth.plugin.api.GitlabApiClient;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.UnsupportedTokenException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.eclipse.sisu.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
@Description("Gitlab Authentication Realm")
public class GitlabAuthenticatingRealm extends AuthorizingRealm {
    private GitlabApiClient gitlabClient;

    public static final String NAME = GitlabAuthenticatingRealm.class.getName();
    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabAuthenticatingRealm.class);

    @Inject
    public GitlabAuthenticatingRealm(final GitlabApiClient gitlabClient) {
        this.gitlabClient = gitlabClient;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected void onInit() {
        super.onInit();
        LOGGER.info("Keycloak Realm initialized...");
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        GitlabPrincipal principal = (GitlabPrincipal) principals.getPrimaryPrincipal();
        LOGGER.info("doGetAuthorizationInfo for user {} with roles {}", principal.getUsername(), String.join("", principal.getGroups()));
        return new SimpleAuthorizationInfo(principal.getGroups());
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        if (!(token instanceof UsernamePasswordToken)) {
            throw new UnsupportedTokenException(String.format("Token of type %s  is not supported. A %s is required.",
                    token.getClass().getName(), UsernamePasswordToken.class.getName()));
        }

        UsernamePasswordToken t = (UsernamePasswordToken) token;
        LOGGER.info("doGetAuthenticationInfo for {}", ((UsernamePasswordToken) token).getUsername());

        GitlabPrincipal authenticatedPrincipal;
        try {
            authenticatedPrincipal = gitlabClient.authz(t.getUsername(), t.getPassword());
            LOGGER.info("Successfully authenticated {}", t.getUsername());
        } catch (GitlabAuthenticationException e) {
            LOGGER.warn("Failed authentication", e);
            return null;
        }

        return createSimpleAuthInfo(authenticatedPrincipal, t);
    }

    /**
     * Creates the simple auth info.
     *
     * @param token the token
     * @return the simple authentication info
     */
    private SimpleAuthenticationInfo createSimpleAuthInfo(GitlabPrincipal principal, UsernamePasswordToken token) {
        return new SimpleAuthenticationInfo(principal, token.getCredentials(), NAME);
    }
}