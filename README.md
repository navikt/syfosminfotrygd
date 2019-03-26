# SYFO sykemelding infotrygd
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
#### Running locally
`./gradlew run`

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`


#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosminfotrygd .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 syfosminfotrygd`

## Contact us
### Code/project related questions can be sent to
* Kevin Sillerud, `kevin.sillerud@nav.no`
* Anders Østby, `anders.ostby@nav.no`
* Joakim Kartveit, `joakim.kartveit@nav.no`

### For NAV employees
We are available at the Slack channel #barken
