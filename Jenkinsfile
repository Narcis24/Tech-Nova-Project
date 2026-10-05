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
                sh 'docker build -t tech-nova:latest ./app'
                sh 'docker build -t tech-nova-pipeline:latest ./data-pipeline'
                sh 'docker build -t tech-nova-auth:latest ./auth'
                sh 'docker build -t tech-nova-execution-engine:latest ./execution-engine'
            }
        }
        stage('Unit Tests') {
            steps {
                // fails the build on test failures; uses the in-memory H2 database, no Postgres or Kafka needed
                sh 'mvn -B -f app/pom.xml test'
                sh 'mvn -B -f auth/pom.xml test'
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
