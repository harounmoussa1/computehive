package tn.eniso.computehive.core.service.restcli;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tn.eniso.computehive.core.infra.security.InternalToken;
import tn.eniso.computehive.core.infra.util.Checks;

import java.net.http.HttpClient;

/**
 * Fabrique des {@link RemoteModuleClient} : un par module distant, chacun avec son adresse et son circuit breaker,
 * tous avec le même jeton interne et les mêmes réglages.
 */
public final class RemoteModuleClients {

    private final RestClient.Builder builder;
    private final InternalToken token;
    private final RemoteCallPolicy policy;

    /** @param builder modèle de client (convertisseurs JSON de l'application) ; il est copié, jamais modifié */
    public RemoteModuleClients(RestClient.Builder builder, InternalToken token, RemoteCallPolicy policy) {
        this.builder = Checks.requireNonNull(builder, "builder");
        this.token = Checks.requireNonNull(token, "token");
        this.policy = Checks.requireNonNull(policy, "policy");
    }

    /**
     * @param module  nom du module distant
     * @param baseUrl adresse de l'application qui l'héberge, ex. {@code http://app-main:8080}
     */
    public RemoteModuleClient create(String module, String baseUrl) {
        Checks.requireNonBlank(baseUrl, "base URL of module " + module);
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(policy.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(policy.readTimeout());
        RestClient rest = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        return new RemoteModuleClient(module, rest, token, policy);
    }
}
