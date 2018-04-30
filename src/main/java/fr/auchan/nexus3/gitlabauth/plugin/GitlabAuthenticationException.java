package fr.auchan.nexus3.gitlabauth.plugin;

public class GitlabAuthenticationException extends Exception{

    public GitlabAuthenticationException(String message){
        super(message);
    }

    public GitlabAuthenticationException(Throwable cause){
        super(cause);
    }

}