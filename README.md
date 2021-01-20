[![Build status](https://github.com/navikt/syfosminfotrygd/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosminfotrygd/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO sykmelding infotrygd
Application for handling rules used for infotrygd and later on persisting them in infotrygd or create a manuall task
to persisting them in infotrygd

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Kafka
* Mq
* Vault

#### Requirements

* JDK 11

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project

repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-commons")
    }
}
```
`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:
   
```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```
#### Running locally
`./gradlew run`

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosminfotrygd .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 syfosminfotrygd`

### Deploy redis to dev:
Deploying redis can be done with the following command:
`kubectl apply --context dev-fss --namespace default -f redis.yaml`

### Deploy redis to prod:
Deploying redis can be done with the following command:
`kubectl apply --context prod-fss --namespace default -f redis.yaml`
