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
            environment {
                POSTGRES_PASSWORD = credentials('postgres-password')
            }
            steps {
                sh '''
                    # Force remove any lingering containers using the port
                    docker-compose down -v --remove-orphans || true
                    docker system prune -f --volumes || true
                    docker ps -a | grep -E "(tech-nova|postgres|kafka)" | awk '{print $1}' | xargs -r docker rm -f || true
                    
                    # Wait for port to be released
                    sleep 5
                    
                    docker-compose up -d
                    sleep 60
                    
                    # Check app logs for successful startup
                    docker-compose logs app | grep -i "started" || (docker-compose logs app && exit 1)
                    
                    docker-compose down -v --remove-orphans
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
