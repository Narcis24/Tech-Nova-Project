pipeline {
    agent any
    environment {
        // the jenkins user has no JAVA_HOME, so Maven would fall back to Java 17 from /etc/java/maven.conf
        JAVA_HOME = '/usr/lib/jvm/java-21-amazon-corretto.x86_64'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('GitLeaks - Secret Scanning') {
            steps {
                // fails the build on any leak; known false positives are listed in .gitleaksignore
                sh 'docker run --rm -v $WORKSPACE:/path ghcr.io/gitleaks/gitleaks:latest detect --source /path --exit-code 1'
            }
        }
        stage('Build Images') {
            steps {
                sh 'docker build -t team-skeleton:latest -f app/Dockerfile .'
                sh 'docker build -t tech-nova-pipeline:latest ./data-pipeline'
                sh 'docker build -t tech-nova-auth:latest ./auth'
                sh 'docker build -t tech-nova-ui:latest ./ui'
                sh 'docker build -t tech-nova-execution-engine:latest -f execution-engine/Dockerfile .'
            }
        }
        stage('UI Checks') {
            steps {
                sh 'docker build --target ci -t tech-nova-ui-ci:latest ./ui'
                sh '''
                    docker run --rm --init --shm-size=1g --user "$(id -u):$(id -g)" \
                      -e CI=true -e HOME=/tmp -v "$WORKSPACE/ui:/workspace/ui" -w /workspace/ui \
                      tech-nova-ui-ci:latest sh -c 'npm ci && npm run check'
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'ui/coverage/**', allowEmptyArchive: true
                }
            }
        }
        stage('Unit Tests') {
            steps {
                // fails the build on test failures; app uses the in-memory H2 database, no Postgres or Kafka needed.
                // Built from the root so messaging comes first; install puts it in ~/.m2 for the per-module Sonar runs
                sh 'mvn -B install'
                // auth is NestJS; the agent's Node is too old, so it runs in a Node 24 container as the jenkins user
                sh 'docker run --rm -u $(id -u):$(id -g) -e npm_config_cache=/tmp/.npm -v $WORKSPACE/auth:/app -w /app node:24-alpine sh -c "npm ci && npm run test:cov"'
            }
        }
        stage('Smoke Test') {
            steps {
                // imports dependencies and compiles load.py without hitting Yahoo or a database
                sh 'docker run --rm --entrypoint python tech-nova-pipeline:latest -c "import load"'
            }
        }
        stage('E2E Tests') {
            steps {
                // the whole system in containers, driven over HTTP; reuses the images built above
                sh 'mvn -B -f e2e/pom.xml verify -De2e.image.app=team-skeleton:latest -De2e.image.auth=tech-nova-auth:latest -De2e.image.engine=tech-nova-execution-engine:latest'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'e2e/target/e2e-logs/*.log', allowEmptyArchive: true
                }
            }
        }
        stage('SonarQube') {
            steps {
                // needs a "Secret text" credential with id sonar-token; fails the build if a quality gate fails
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    // same script as local runs; it re-runs the auth tests for coverage
                    sh './sonar/scan.sh'
                }
            }
        }
        stage('Trivy - Vulnerability Scanning') {
            steps {
                // Bundled browser dependencies are not discoverable from the Nginx image.
                sh 'docker run --rm -v "$WORKSPACE/ui/package-lock.json:/scan/package-lock.json:ro" -v trivy-cache:/root/.cache aquasec/trivy:latest fs --scanners vuln --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed /scan'
                // scans each image's full dependency tree (JARs, Python packages, OS packages);
                // fails the build on any HIGH or CRITICAL vulnerability that has a fix available
                script {
                    for (img in ['team-skeleton', 'tech-nova-pipeline', 'tech-nova-auth', 'tech-nova-execution-engine', 'tech-nova-ui']) {
                        sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache aquasec/trivy:latest image --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed ${img}:latest"
                    }
                }
            }
        }
    }
}
