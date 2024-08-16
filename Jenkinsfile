/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

@Library('hibernate-jenkins-pipeline-helpers@1.13') _

// Perform authenticated pulls of container images, to avoid failure due to download throttling on dockerhub.
def pullContainerImages() {
    docker.withRegistry('https://index.docker.io/v1/', 'hibernateci.hub.docker.com') {
        docker.image("neo4j:$NEO4J_VERSION").pull()
        docker.image("infinispan/server:$INFINISPAN_VERSION").pull()
    }
}

def withMavenWorkspace(Closure body) {
    withMaven(jdk: 'OpenJDK 8 Latest', maven: 'Apache Maven 3.3',
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
        NEO4J_HOSTNAME = 'localhost'

        BOLT_NEO4J_PORT = 7687
        HTTP_NEO4J_PORT = 7474
        NEO4J_PORT = 7474

        NEO4J_USERNAME = 'neo4j'
        NEO4J_PASSWORD = 'jenkins'
        NEO4J_VERSION = '3.4.1'
        INFINISPAN_VERSION = '10.0.1.Final'
    }
    stages {
        stage('Build') {
            agent {
                label 'Worker&&Containers'
            }
            stages {
//				stage('Start Neo4j') {
//					steps {
//						echo 'Starting Neo4j'
//						script {
//							pullContainerImages()
//							sh "docker stop Neo4j || true"
//							sh "docker rm -f Neo4j || true"
//
//							sh "docker run --rm -d -p $BOLT_NEO4J_PORT:7687 -p $HTTP_NEO4J_PORT:7474 --name Neo4j neo4j:$NEO4J_VERSION"
//						}
//						echo 'Checking Neo4j is up and running'
//						script {
//							sh """while true
//							do
//							  STATUS="\$(curl -s -o /dev/null -w '%{http_code}' -H 'Authorization: Basic bmVvNGo6bmVvNGo=' http://localhost:$HTTP_NEO4J_PORT)" || true
//							  if [ "\$STATUS" -eq 200 ]; then
//								break
//							  fi
//							  sleep 20
//							done
//							"""
//						}
//						echo 'Setting credentials'
//						script {
//							sh """curl \
//							  -X POST \
//							  -H "Content-Type: application/json" -H "Authorization: Basic `echo -n 'neo4j:neo4j' | base64`" \
//							  -d "{\\"password\\":\\"$NEO4J_PASSWORD\\"}" \
//							  http://localhost:$HTTP_NEO4J_PORT/user/neo4j/password
//							"""
//						}
//						echo "Validating Neo4j creadentials"
//						script {
//							sh "curl --user $NEO4J_USERNAME:$NEO4J_PASSWORD http://localhost:$HTTP_NEO4J_PORT/db/data"
//						}
//					}
//				}
                stage('Quick build skipping tests') {
                    agent {
                        label 'Worker&&Containers'
                    }
                    steps {
                        withMavenWorkspace {
                            sh "mvn -U clean install -e -DskipTests"

                            dir(env.WORKSPACE_TMP + '/.m2repository') {
                                stash name: 'original-build-result', includes: "org/hibernate/ogm/**"
                            }
                        }
                    }
                }
                stage('Build') {
//					post {
//						cleanup {
//							sh 'docker stop Neo4j || true'
//							sh 'docker rm -f Neo4j || true'
//						}
//					}
                    options {
                        timeout(time: 1, unit: 'HOURS')
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
                                    sh "mvn -U clean install -e --fail-at-end"
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
