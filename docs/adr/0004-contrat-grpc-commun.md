# ADR 0004 — `worker.proto` comme contrat unique avec l'agent

- **Statut** : proposée (à valider avec le binôme)
- **Date** : 2026-11-20

## Contexte

Le serveur (Java) et l'agent (Rust, développé par le binôme) sont deux programmes séparés.
La première version du README-AGENT et `CLAUDE.md` décrivaient deux protocoles différents
(noms de services, de messages, sens du mot « epoch »).

## Décision

- Un seul fichier, `contracts/worker-proto/src/main/proto/worker.proto`, paquetage
  `computehive.worker.v1`. Le serveur génère son code Java avec `protobuf-maven-plugin` ;
  l'agent génère le sien (`tonic` / `prost`) depuis le même fichier.
- Le contenu est celui de la proposition `docs/worker-protocol.md` §5 : stream bidirectionnel
  ouvert par l'agent, `message_id` (dédoublonnage), `command_id` + `CommandAck` (idempotence),
  `attempt_id` comme fencing token, `Registered` pour que le serveur fixe heartbeat et bail.
- Toute modification passe par une PR relue par les deux membres ; aucun champ n'est
  renuméroté ni réutilisé.

## Alternatives écartées

- REST + WebSocket : pas de contrat typé partagé, et gRPC fait partie de la stack imposée.
- Un `.proto` par dépôt : les deux copies finiraient par diverger.
