package tn.eniso.computehive.contracts.remote;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tn.eniso.computehive.core.infra.security.InternalToken;
import tn.eniso.computehive.core.service.restcli.RemoteCallPolicy;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClient;
import tn.eniso.computehive.core.ws.rest.ApiExceptionHandler;
import tn.eniso.computehive.core.ws.rest.InternalApiFilter;

/**
 * Relie un proxy {@code service-rest-cli} au vrai contrôleur interne de son module, sans réseau : les requêtes du
 * proxy sont servies par MockMvc, avec le filtre de l'API interne, la traduction des erreurs et les réglages JSON de
 * Spring Boot. Seule la façade, derrière le contrôleur, est simulée.
 */
final class RemoteContract {

    static final InternalToken TOKEN = InternalToken.of("contract-token");

    private RemoteContract() {
    }

    /** Client du module {@code module}, servi par {@code controller}, qui présente le bon jeton interne. */
    static RemoteModuleClient client(String module, Object controller) {
        return client(module, controller, TOKEN);
    }

    /** Client qui présente {@code presented} à une API interne qui attend {@link #TOKEN}. */
    static RemoteModuleClient client(String module, Object controller, InternalToken presented) {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(json())
                .addFilters(new InternalApiFilter(TOKEN))
                .build();
        RestClient rest = RestClient.builder()
                .baseUrl("http://remote-app")
                .requestFactory(new MockMvcClientHttpRequestFactory(mvc))
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(json());
                })
                .build();
        return new RemoteModuleClient(module, rest, presented, RemoteCallPolicy.defaults());
    }

    /** Mêmes réglages JSON que Spring Boot : dates et durées au format ISO-8601. */
    private static MappingJackson2HttpMessageConverter json() {
        return new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .build());
    }
}
