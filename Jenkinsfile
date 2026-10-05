pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('GitLeaks - Secret Scanning') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh 'docker run --rm -v $WORKSPACE:/path ghcr.io/gitleaks/gitleaks:latest detect --source /path --exit-code 1 || true'
                }
            }
        }
        stage('Build Images') {
            steps {
                sh 'docker build -t tech-nova:latest ./app'
                sh 'docker build -t tech-nova-pipeline:latest ./data-pipeline'
            }
        }
        stage('Smoke Test') {
            steps {
                sh '''
                    export POSTGRES_PASSWORD=test-password
                    docker-compose up -d
                    sleep 60
                    docker-compose exec -T app curl -f http://localhost:8081/api/actuator/health || exit 1
                    docker-compose down
                '''
                sh 'docker run --rm --entrypoint python tech-nova-pipeline:latest -c "import load"'
            }
        }
        stage('SonarQube') {
            steps {
                // needs a "Secret text" credential with id sonar-token; logs a warning instead of failing the build
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh 'mvn -B -f app/pom.xml verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=$SONAR_TOKEN'
                        sh 'mvn -B -f auth/pom.xml verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=$SONAR_TOKEN'
                    }
                }
            }
        }
        stage('Dependency-Check - Vulnerability Scanning') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh 'mvn -B -f app/pom.xml dependency-check:aggregate'
                    sh 'mvn -B -f auth/pom.xml dependency-check:aggregate'
                }
            }
        }
    }
}
