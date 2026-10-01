# Duels — plugin de duels pour Spigot 1.8.8

Duels 1v1 avec file d'attente, défis directs, parties entre amis (FFA, équipes, party contre party), menus GUI, kits configurables et arènes **copiées à la volée** : chaque match se joue sur une copie neuve de la map, détruite à la fin. Une map cassée est donc toujours « réparée » pour le match suivant.

## Installation

1. Copier `target/Duels.jar` dans `plugins/`, démarrer le serveur.
2. Se placer au lobby puis `/duels setlobby`.
3. Créer au moins une arène (voir plus bas). 5 kits d'exemple sont créés automatiquement : `nodebuff`, `builduhc`, `sumo`, `combo`, `archer`.

> Le plugin est pensé pour un serveur (ou un lobby) dédié aux duels : à la connexion, l'inventaire est vidé et remplacé par les objets du lobby.

### Recompiler

```bash
mvn package
```

Le jar est produit dans `target/Duels.jar`. Sans Maven, il suffit de compiler avec `javac` contre `spigot-api-1.8.8-R0.1-SNAPSHOT.jar`.

## Fonctionnement des maps

| Monde | Rôle |
|---|---|
| `duels_tpl_<arène>` | Map **modèle**. Elle reste déchargée, sauf pendant `/arena edit`. |
| `duels_match_<arène>_<n>` | **Copie jetable** chargée au début d'un match, puis déchargée et supprimée à la fin. |

- Quand 2 joueurs choisissent le même mode, le plugin prend une arène **disponible** : activée, avec au moins 2 spawns, compatible avec le kit et sous sa limite `max-instances`. Il copie le monde modèle en asynchrone, le charge, puis téléporte les joueurs.
- `max-instances` fixe le nombre de matchs simultanés sur une même map. Avec `1`, une map n'accueille qu'un match à la fois. `worlds.max-loaded-instances` plafonne le total sur le serveur.
- Les copies restées sur le disque après un crash sont supprimées au démarrage.
- Le chargement des chunks de spawn est désactivé sur ces mondes pour que les copies se chargent vite.

## Créer une arène

```text
/arena create <nom>              → crée un monde vide (plateforme de verre) et t'y téléporte en créatif
/arena create <nom> <monde>      → OU copie un monde existant (déjà construit) comme modèle
   ... construire la map ...
/arena addspawn <nom>            → spawn n°1 (équipe 1)
/arena addspawn <nom>            → spawn n°2 (équipe 2) — ajoute-en d'autres pour les FFA
/arena setspec <nom>             → point des spectateurs (optionnel)
/arena save <nom>                → sauvegarde et décharge la map : elle est prête
```

Réglages facultatifs :

- `/arena kits <nom> add <kit>` limite la map à certains kits (sans réglage, tous les kits sont acceptés, par exemple une map Sumo uniquement pour `sumo`).
- `/arena set <nom> maxinstances 3` règle le nombre de matchs simultanés.
- `/arena set <nom> voidy 40` : un joueur qui descend sous cette hauteur est éliminé (utile pour les maps flottantes, Sumo…).
- `/arena set <nom> buildlimit 100` règle la hauteur maximale de construction.
- `/arena set <nom> displayname &aForêt` et `/arena set <nom> icon` (avec l'objet en main) règlent l'affichage dans les menus.
- `/arena edit <nom>` modifie la map plus tard. Les matchs en cours ne sont pas touchés : ils jouent sur des copies.

## Créer une arène depuis une schématique

Formats acceptés :

- **`.schem`** : format Sponge v1, v2 ou v3, produit par WorldEdit 7+ ou FAWE, en 1.13 ou plus. Les blocs modernes sont convertis automatiquement en blocs 1.8.
- **`.schematic`** : format MCEdit, produit par WorldEdit 6 en 1.8. Les blocs sont déjà au format 1.8.

1. Déposer les fichiers dans `plugins/Duels/schematics/`. Le plugin lit aussi `plugins/WorldEdit/schematics/` et `plugins/FastAsyncWorldEdit/schematics/` (liste modifiable dans `config.yml`, section `schematics.folders`).
2. Créer l'arène :

```text
/arena schematics                         → liste les schématiques trouvées
/arena fromschem <nom> <fichier>          → crée un monde vide, y colle la map et t'y téléporte
   (équivalent : /arena create <nom> <fichier.schem>)
/arena addspawn <nom>  (x2 ou plus)       → puis /arena setspec <nom>
/arena save <nom>
```

- La map est centrée en `0 / 64 / 0`. La hauteur `64` se règle avec `schematics.paste-y`. La « zone de vide » mortelle est placée automatiquement 5 blocs sous la map.
- Le collage se fait par morceaux de 40 000 blocs par tick (`schematics.blocks-per-tick`), avec l'avancement affiché en %, pour ne pas figer le serveur.
- `/arena paste <nom> <fichier>` colle une schématique **à ta position**, comme `//paste`, dans une arène en cours d'édition. Pratique pour assembler une map en plusieurs morceaux.

**Conversion 1.13+ → 1.8.** Chaque bloc moderne est converti avec la table de WorldEdit, en gardant l'orientation des escaliers, dalles, bûches, portes, panneaux, etc. Les blocs qui n'existent pas en 1.8 sont remplacés par un équivalent proche, par exemple :

| Bloc moderne | Remplacé par |
|---|---|
| béton | laine de la même couleur |
| terre cuite émaillée | argile colorée |
| bois crimson / warped / cerisier | chêne |
| deepslate | cobblestone |
| lanterne | air |

À la création, le plugin liste les types de blocs qu'il a dû approximer.

**Limites.** L'air n'est pas collé. Le contenu des blocs spéciaux n'est pas copié : texte des panneaux, contenu des coffres, bannières.

## Configurer les kits

En jeu : s'équiper (inventaire, armure, effets de potion) puis :

```text
/dkit create <nom>                   → crée le kit depuis ton inventaire (icône = objet en main)
/dkit setinv <nom>                   → remplace le contenu
/dkit load <nom>                     → récupérer le kit pour le modifier (passe en créatif pour réorganiser)
/dkit setname <nom> &bNoDebuff
/dkit seticon <nom>
/dkit set <nom> <réglage> <valeur>
```

| Réglage | Effet |
|---|---|
| `build` | Autorise la construction (BuildUHC). |
| `break-map` | Autorise aussi à casser la map (sinon, seuls les blocs posés par les joueurs se cassent). |
| `hunger` / `regen` | Faim / régénération naturelle. |
| `no-damage` | Coups sans dégâts, knockback conservé (Sumo). |
| `water-kills` | Toucher l'eau élimine (Sumo). |
| `hit-delay` | Délai entre deux coups en ticks (20 = vanilla, 2 = Combo). |
| `queue` | Le kit apparaît dans le menu « Jouer ». |
| `enabled` | Active ou désactive le kit. |

Tout est aussi modifiable à la main dans `kits.yml`, puis `/duels reload`.

## Joueurs

| Objet / commande | Action |
|---|---|
| Épée « Jouer » / `/queue` | Menu des modes : un clic rejoint la file du kit. |
| Boussole / `/spectate [joueur]` | Liste des matchs en cours, à regarder. |
| `/duel <joueur> [kit] [arène]` | Défi direct : menu kit, puis map, avec message cliquable [ACCEPTER] / [REFUSER]. |
| Étiquette « Créer une party » / `/party` | Menu de party. |
| `/leave` | Quitter la file, le match (abandon) ou le mode spectateur. |
| `/stats [joueur]` | Victoires, défaites, kills, morts. |

### Parties

- `/party invite <joueur>` (message cliquable), `/party accept <chef>`, `/party open` pour une party publique.
- Le chef lance depuis le menu :
  - **FFA** : chacun pour soi ;
  - **Équipes** : la party est coupée en 2 équipes aléatoires ;
  - **Défier une party** : party contre party, acceptée par l'autre chef.
- Chat de party : `@message` ou `/party chat <msg>`.

## Fichiers

| Fichier | Contenu |
|---|---|
| `config.yml` | Lobby, objets du lobby, mondes, règles de match, parties. |
| `messages.yml` | Tous les textes, en français, avec les couleurs `&`. |
| `kits.yml` / `arenas.yml` | Kits et arènes, écrits par les commandes. |
| `lobby.yml` / `stats.yml` | Position du lobby et statistiques. |

`legacy-blocks.txt` (embarqué dans le jar) est la table de conversion des blocs. Elle vient de [WorldEdit](https://github.com/EngineHub/WorldEdit), sous licence GPL-3.0.

Permission : `duels.admin` (op par défaut) donne accès à `/arena`, `/dkit`, `/duels setlobby|reload`, à la construction au lobby en créatif et aux commandes pendant un match.
