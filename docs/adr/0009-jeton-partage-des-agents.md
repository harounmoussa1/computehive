# ADR 0009 — Authentification des agents par jeton partagé (v1)

- **Statut** : acceptée
- **Date** : 2026-11-22

## Contexte

Jusqu'ici, n'importe quel programme qui connaissait l'adresse du serveur pouvait ouvrir
`WorkerGateway.Connect`, s'inscrire comme machine, recevoir des jobs, envoyer de faux `Finished`, ou
déposer des fichiers sur `/api/checkpoints`. Dès que des agents tournent sur d'autres PC du laboratoire,
le serveur doit savoir qu'il parle à un agent du labo. Le protocole repoussait TLS et l'enrôlement des
agents en v2 (docs/worker-protocol.md §9).

## Décision

1. **Un secret commun à tous les agents** du labo, réglé sur le serveur par
   `computehive.agents.token` (`COMPUTEHIVE_AGENTS_TOKEN`) et représenté par `AgentToken` (`core-infra`).
2. **Présenté dans l'en-tête standard `authorization: Bearer <jeton>`**, à l'ouverture du stream gRPC
   (metadata) et sur chaque transfert de checkpoint. `worker.proto` ne change pas.
3. **Deux gardes, une par canal**, qui refusent avant toute logique :
   - `AgentTokenInterceptor` (intercepteur gRPC) : `UNAUTHENTICATED` avant même `Register` ;
   - `CheckpointAuthenticationFilter` (filtre Servlet sur `/api/checkpoints/**`) : `401`.
4. **Comparaison à temps constant** (`MessageDigest.isEqual`) : la durée de la vérification ne renseigne
   pas sur la partie correcte d'un jeton essayé. Le jeton n'apparaît jamais dans les logs.
5. **Sans jeton configuré, la vérification est désactivée** et l'application l'écrit au démarrage : le
   développement et les tests sur un seul PC restent simples. Le `docker-compose` du mode M/P fixe un jeton.

## Conséquences

- Un programme qui ne connaît pas le jeton ne peut ni s'inscrire, ni recevoir un ordre, ni déposer un fichier.
- Tous les agents partagent le même secret : on ne peut pas en révoquer un seul ; changer le jeton
  oblige à le redistribuer à tous les PC.
- Sans TLS, le jeton circule en clair : il protège le réseau fermé du laboratoire, pas Internet.

## Alternatives écartées

- **Aucune authentification** (situation précédente) : tout programme du réseau pouvait se faire passer
  pour un agent.
- **mTLS (certificat par agent)** : la bonne solution à terme, mais elle demande une autorité de
  certification et un enrôlement de chaque PC : v2.
- **Un jeton dans le message `Register`** : il faudrait modifier `worker.proto`, et l'API REST des
  checkpoints resterait sans protection ; l'en-tête standard couvre les deux canaux d'un coup.
- **Filtrer par adresse IP** : les PC du labo changent d'adresse (DHCP, Wi-Fi), et une adresse se falsifie.
