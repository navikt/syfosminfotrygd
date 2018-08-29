# SYFO sykemelding infotrygd
Application for handling rules used for infotrygd and later on persisting them

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Kafka

## Getting started
### Running locally
Unless you change kafka_consumer.properties you will need to either connect to one of NAVs kafka clusters or use the
[docker compose](https://github.com/navikt/navkafka-docker-compose) environment to test it against. To run it the
environment variables `SYFOSMINFOTRYGD_USERNAME` and `SYFOSMINFOTRYGD_PASSWORD` needs to be set to
a user that has access to the topic defined by the environment variable `KAFKA_SM2013_JOURNALING_TOPIC`


### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew installDist` or  on windows 
`gradlew.bat installDist`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosminfotrygd .`


## Contact us
### Code/project related questions can be sent to
* Kevin Sillerud, `kevin.sillerud@nav.no`
* Anders Østby, `anders.ostby@nav.no`
* Joakim Kartveit, `joakim.kartveit@nav.no`

### For NAV employees
We are available at the Slack channel #barken
