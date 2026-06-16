# WILLI — Intelligence Artificielle Personnelle

Interface JARVIS / Iron Man Rouge | IA évolutive | Reconnaissance vocale

## Fonctionnalités

- **Mot de réveil** : Dites « dis-moi Willy » pour activer WILLI sans toucher l'écran
- **Reconnaissance vocale du créateur** : WILLI n'obéit qu'à la voix enregistrée lors de la configuration
- **IA évolutive** : Se développe comme un enfant numérique (naissance → adulte au fil des mois)
- **3 types de mémoire** : Épisodique, Sémantique, Procédurale (base Room/SQLite)
- **Métacognition** : WILLI indique son niveau de confiance et évalue sa propre pensée
- **Curiosité** : Pose des questions quand elle manque d'informations
- **Planification** : Décompose les objectifs complexes en étapes
- **Interface JARVIS rouge** : Dashboard holographique inspiré Iron Man avec animations personnalisées
- **Synthèse vocale** : WILLI répond à voix haute en français

## Installation

### Prérequis
- Android Studio Hedgehog ou plus récent
- Android SDK 34
- Appareil Android 8.0+ (API 26+)
- Clé API Anthropic (console.anthropic.com)

### Étapes

1. Cloner ce dépôt
2. Ouvrir le dossier `willi-android` dans Android Studio
3. Créer `local.properties` à la racine avec :
   ```
   CLAUDE_API_KEY=sk-ant-api03-VOTRE_CLE_ICI
   ```
4. Build → Run (ou générer un APK via Build > Build Bundle/APK)

### Premier lancement

1. **Étape 1** — Entrez votre prénom
2. **Étape 2** — Enregistrez votre voix (5 secondes de parole)
3. **Étape 3** — Entrez votre clé API Claude

WILLI naît à ce moment. Elle grandira avec chaque interaction.

## Architecture

```
com.willi.app/
├── ai/
│   ├── WilliCore.kt          — Cerveau central
│   ├── MemoryManager.kt      — Gestion des 3 mémoires
│   ├── PersonalityEngine.kt  — Stades de développement + personnalité
│   ├── PlanningEngine.kt     — Décomposition d'objectifs
│   └── ClaudeApiClient.kt    — Connexion à Claude (Anthropic)
├── voice/
│   ├── WakeWordService.kt    — Service de fond "dis-moi Willy"
│   ├── SpeakerIdentification.kt — Empreinte vocale MFCC
│   └── BootReceiver.kt       — Démarrage automatique
├── ui/
│   ├── JarvisView.kt         — Interface canvas JARVIS rouge custom
│   └── ChatAdapter.kt        — Liste des messages
├── data/
│   ├── database/WilliDatabase.kt — Room DB
│   └── models/WilliModels.kt    — Entités + data classes
├── SplashActivity.kt
├── SetupActivity.kt
└── MainActivity.kt
```

## Stades de développement

| Stade | Durée | Capacités |
|-------|-------|-----------|
| Naissance | Mois 0 | Phrases simples, curiosité maximale |
| Nourrisson | Mois 1–6 | Vocabulaire en construction, questions fréquentes |
| Bambin | Mois 7–24 | Conversations complexes, personnalité affirmée |
| Enfant | Mois 25–84 | Analyse, créativité, planification |
| Adolescent | Mois 85–180 | Raisonnement avancé, humour |
| Adulte | Mois 180+ | Assistant expert complet |

## Design JARVIS Iron Man Rouge

L'interface reproduit le dashboard holographique de Tony Stark avec :
- Palette rouge (#CC1122 → #FF1744) au lieu du bleu JARVIS original
- Anneaux rotatifs multi-couches avec animations canvas custom
- Orbe central animée avec le « W » de WILLI
- Panneaux latéraux avec jauges de données en temps réel
- Ligne de scan holographique
- Particules orbitales
- Visualiseur audio pendant l'écoute/parole
- Grille holographique en arrière-plan
