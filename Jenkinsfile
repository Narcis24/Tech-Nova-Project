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
    }
}
