package tn.eniso.computehive.architecture.fixture.modules.alpha.service.impl;

import tn.eniso.computehive.architecture.fixture.extensions.broker.api.Broker;
import tn.eniso.computehive.architecture.fixture.modules.beta.service.api.BetaModule;

/** Autorisé : façade d'un autre module et contrat d'une extension. */
public class AlphaNotifier {
    BetaModule beta;
    Broker broker;
}
