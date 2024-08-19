/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

@Library('hibernate-jenkins-pipeline-helpers@1.13') _

// Perform authenticated pulls of container images, to avoid failure due to download throttling on dockerhub.
def pullContainerImages() {
    docker.withRegistry('https://index.docker.io/v1/', 'hibernateci.hub.docker.com') {
        docker.image("neo4j:$NEO4J_VERSION").pull()
    }
}

def withMavenWorkspace(Closure body) {
    withMaven(jdk: 'OpenJDK 8 Latest', maven: 'Apache Maven 3.9',
            mavenLocalRepo: env.WORKSPACE_TMP + '/.m2repository',
            mavenSettingsConfig: 'settings-example.xml',
            options: [
                    // Artifacts are not needed and take up disk space
                    artifactsPublisher(disabled: true),
            ]) {
        body()
    }
}

pipeline {
    agent none
    options {
        buildDiscarder logRotator(daysToKeepStr: '10', numToKeepStr: '3')
        disableConcurrentBuilds(abortPrevious: true)
        overrideIndexTriggers(false)
    }
    environment {
        NEO4J_VERSION = '3.4.1'
    }
    stages {
        stage('Build') {
            agent {
                label 'Worker&&Containers'
            }
            stages {
                stage('Quick build skipping tests') {
                    agent {
                        label 'Worker&&Containers'
                    }
                    steps {
                        sh 'ci/docker-cleanup.sh'

                        withMavenWorkspace {
                            sh "mvn -v"
                            sh "mvn -U clean install -e -DskipTests"

                            dir(env.WORKSPACE_TMP + '/.m2repository') {
                                stash name: 'original-build-result', includes: "org/hibernate/ogm/**"
                            }
                        }
                    }
                }
                stage('Build') {
                    options {
                        timeout(time: 1, unit: 'HOURS')
                    }
                    post {
                        cleanup {
                            sh 'ci/docker-cleanup.sh'
                        }
                    }
                    parallel {
                        stage('Default build with tests') {
                            agent {
                                label 'Worker&&Containers'
                            }
                            steps {
                                withMavenWorkspace {
                                    dir(env.WORKSPACE_TMP + '/.m2repository') {
                                        unstash name: 'original-build-result'
                                    }
                                    sh "mvn -v"
                                    sh "mvn -U clean install -pl :hibernate-ogm-infinispan-remote -e --fail-at-end"
                                }
                            }
                        }
                        stage('neo4j-http') {
                            agent {
                                label 'Worker&&Containers'
                            }
                            steps {
                                withMavenWorkspace {
                                    dir(env.WORKSPACE_TMP + '/.m2repository') {
                                        unstash name: 'original-build-result'
                                    }
                                    sh "mvn clean install -e -Pneo4j-http -pl neo4j --fail-at-end"
                                }
                            }
                        }
                        stage('neo4j-bolt') {
                            agent {
                                label 'Worker&&Containers'
                            }
                            steps {
                                withMavenWorkspace {
                                    dir(env.WORKSPACE_TMP + '/.m2repository') {
                                        unstash name: 'original-build-result'
                                    }
                                    sh "mvn clean install -e -Pneo4j-bolt -pl neo4j --fail-at-end"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            notifyBuildResult maintainers: 'marko@hibernate.org'
        }
    }
}
