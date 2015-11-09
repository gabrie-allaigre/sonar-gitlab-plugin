Sonar GitLab Plugin
===================

[![build status](https://gitlab.synaptix-labs.com/ci/projects/12/status.png?ref=master)](https://gitlab.synaptix-labs.com/ci/projects/12?ref=master)

Fork to https://github.com/SonarCommunity/sonar-github

# But

Ajoute pour chaque **commit** dans GitLab, un commentaire global sur les nouvelles anomalies ajoutées par ce **commit** et commente les lignes des fichiers.

# Usage

Pour ajouter le plugin SonarQube :

- Télécharger la dernière version du plugin http://nexus.synaptix-labs.com/service/local/repo_groups/public_release/content/com/synaptix/sonar-gitlab-plugin/1.0.0/sonar-gitlab-plugin-1.1.0.jar
- Copier le fichier dans le répertoire `SONARQUBE_HOME/extensions/plugins`
- Relancer SonarQube 

# Ligne de commande

Exemple :

``` shell
mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.issuesReport.console.enable=true -Dsonar.gitlab.commit_sha=$CI_BUILD_REF -Dsonar.gitlab.ref=CI_BUILD_REF_NAME
```

| Variable | Commentaire | Type |
| -------- | ----------- | ---- |
| sonar.gitlab.url | Adresse du GitLab | Administration, Variable |
| sonar.gitlab.max_global_issues | Nombre maximum d'anomalie à afficher dans le commentaire global |  Administration, Variable |
| sonar.gitlab.user_token | Token de l'utilisateur qui peut faire des rapports sur le projet, soit global ou par projet |  Administration, Projet, Variable |
| sonar.gitlab.project_id | Identifiant du projet dans GitLab, soit id interne, soit namespace+name, soit namespace+path, soit http url, soit ssh url ou soit web url | Projet, Variable |
| sonar.gitlab.commit_sha | SHA du commit à commenter | Variable |
| sonar.gitlab.ref_name | nom de la branche ou reference du commit | Variable |

- Administration : Dans les **Settings** globals de SonarQube
- Projet : Dans les **Settings** du projet de SonarQube
- Variable : En variable d'environement, soit dans le `pom.xml` soit en ligne de commande avec `-D`