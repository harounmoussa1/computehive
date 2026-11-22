package tn.eniso.computehive.architecture.fixture.modules.beta.service.impl;

import tn.eniso.computehive.architecture.fixture.modules.beta.service.api.BetaModule;
import tn.eniso.computehive.architecture.fixture.modules.beta.service.restcli.BetaRestClient;

/** Fautif sur un champ : il choisit lui-même le proxy distant, au lieu de recevoir la façade. */
public class BetaPlanner {
    BetaRestClient remote;
    BetaModule facade;
}
