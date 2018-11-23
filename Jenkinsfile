#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosminfotrygd'
        ZONE = 'fss'
        DOCKER_SLUG='syfo'
        FASIT_ENVIRONMENT='q1'
        DISABLE_SLACK_MESSAGES = true
    }

    stages {
        stage('initialize') {
            steps {
                script {
                    init action: 'default'
                    sh './gradlew clean'
                    applicationVersionGradle = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
                    env.APPLICATION_VERSION = "${applicationVersionGradle}"
                    if (applicationVersionGradle.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${applicationVersionGradle}.${env.BUILD_ID}-${env.COMMIT_HASH_SHORT}"
                    }
                    init action: 'updateStatus'
                }
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('deploy') {
            steps {
                dockerUtils action: 'createPushImage'
                nais action: 'validate'
                nais action: 'upload'
                deployApp action: 'jiraPreprod'
            }
        }
    }
    post {
        always {
            postProcess action: 'always'
            archiveArtifacts artifacts: 'build/reports/rules.csv', allowEmptyArchive: true
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
