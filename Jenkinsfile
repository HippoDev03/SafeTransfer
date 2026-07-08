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
                // -e for full stack traces if this ever fails again - the
                // javadoc plugin's own error detection has been unreliable
                // (see pom.xml comment), so we verify the real output below
                // rather than trusting a clean exit code alone.
                sh 'mvn -B -ntp -e site'
                script {
                    if (!fileExists('target/site/index.html')) {
                        error 'javadoc:javadoc reported success but target/site/apidocs/index.html was not produced - check the mvn output above for the real error.'
                    }
                }
                javadoc javadocDir: 'target/site/', keepAll: true
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
