package tn.eniso.computehive.architecture.fixture.modules.alpha.service.impl;

import tn.eniso.computehive.architecture.fixture.extensions.broker.impl.BrokerInternals;
import tn.eniso.computehive.architecture.fixture.modules.alpha.dal.api.AlphaRepository;
import tn.eniso.computehive.architecture.fixture.modules.beta.dal.api.BetaRepository;

/** Fautif : service métier qui contourne le CRUD, la façade de beta et le contrat de l'extension. */
public class AlphaBillingService {
    AlphaRepository ownRepository;
    BetaRepository otherModuleRepository;
    BrokerInternals extensionInternals;
}
