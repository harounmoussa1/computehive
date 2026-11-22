package tn.eniso.computehive.architecture.fixture.modules.beta.service.restcli;

import tn.eniso.computehive.architecture.fixture.modules.beta.dal.api.BetaRepository;
import tn.eniso.computehive.architecture.fixture.modules.beta.service.api.BetaModule;

/** Fautif : un proxy qui lit la base au lieu d'appeler le module distant. */
public class BetaRestClient implements BetaModule {
    BetaRepository shortcut;
}
