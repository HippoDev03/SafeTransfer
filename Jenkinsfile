pipeline {
    agent any

    tools {
        maven 'Maven 3'
        jdk 'JDK 21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -ntp clean package'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/SafeTransfer-*.jar', fingerprint: true
            }
        }

        stage('Javadoc') {
            steps {
                sh 'mvn -B -ntp javadoc:javadoc'
                javadoc javadocDir: 'target/site/apidocs', keepAll: true
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        cleanup {
            cleanWs()
        }
    }
}
