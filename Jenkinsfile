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
                sh 'docker build -t team-skeleton:latest ./app'
                sh 'docker build -t tech-nova-pipeline:latest ./data-pipeline'
                sh 'docker build -t tech-nova-auth:latest ./auth'
                sh 'docker build -t tech-nova-execution-engine:latest ./execution-engine'
            }
        }
        stage('Unit Tests') {
            steps {
                // fails the build on test failures; app uses the in-memory H2 database, no Postgres or Kafka needed
                sh 'mvn -B -f app/pom.xml test'
                sh 'mvn -B -f auth/pom.xml test'
                sh 'mvn -B -f execution-engine/pom.xml test'
            }
        }
        stage('Smoke Test') {
            steps {
                // imports dependencies and compiles load.py without hitting Yahoo or a database
                sh 'docker run --rm --entrypoint python tech-nova-pipeline:latest -c "import load"'
            }
        }
        stage('SonarQube') {
            steps {
                // needs a "Secret text" credential with id sonar-token; fails the build if a quality gate fails
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    script {
                        for (m in ['app', 'auth', 'execution-engine']) {
                            sh "mvn -B -f ${m}/pom.xml verify sonar:sonar -Dsonar.host.url=http://localhost:8083 -Dsonar.token=\$SONAR_TOKEN -Dsonar.qualitygate.wait=true"
                        }
                    }
                }
            }
        }
        stage('Trivy - Vulnerability Scanning') {
            steps {
                // scans each image's full dependency tree (JARs, Python packages, OS packages);
                // fails the build on any HIGH or CRITICAL vulnerability that has a fix available
                script {
                    for (img in ['team-skeleton', 'tech-nova-pipeline', 'tech-nova-auth', 'tech-nova-execution-engine']) {
                        sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache aquasec/trivy:latest image --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed ${img}:latest"
                    }
                }
            }
        }
    }
}
