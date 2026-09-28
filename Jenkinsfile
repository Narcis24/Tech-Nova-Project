pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Images') {
            steps {
                sh 'docker build -t team-skeleton:latest ./app'
                sh 'docker build -t tech-nova-pipeline:latest ./data-pipeline'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm team-skeleton:latest'
                // imports dependencies and compiles load.py without hitting Yahoo or a database
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
    }
}
