Sonar GitLab Plugin
===================

[![build status](https://gitlab.synaptix-labs.com/ci/projects/12/status.png?ref=master)](https://gitlab.synaptix-labs.com/ci/projects/12?ref=master)

Fork to https://github.com/SonarCommunity/sonar-github

# Usage

Pour ajouter le plugin SonarQube :

- Télécharger la dernière version du plugin http://nexus.synaptix-labs.com/service/local/repo_groups/public_release/content/com/synaptix/sonar-gitlab-plugin/1.0.0/sonar-gitlab-plugin-1.0.0.jar
- Copier le fichier dans le répertoire `SONARQUBE_HOME/extensions/plugins`
- Relancer SonarQube

Dans administration vous pouvez paramètrer l'adresse du GitLab par défaut et le *token* de l'utilisateur qui a le droit de faire les rapports 