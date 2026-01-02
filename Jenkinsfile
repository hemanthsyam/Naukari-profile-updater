pipeline {
    agent any

    tools {
        // Jenkins will automatically install these
        maven 'Maven 3.9.12'
        jdk 'JDK11'
    }

    environment {
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                bat """
                    echo "Java:"
                    java -version
                    echo.
                    echo "Maven:"
                    mvn --version
                """
            }
        }

        stage('Build') {
            steps {
                bat 'mvn clean compile'
            }
        }

        stage('Run Automation') {
            steps {
                bat 'mvn exec:java -Dexec.mainClass="com.automation.NaukriProfileUpdater"'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
        }
        success {
            echo '✅ Profile updated successfully!'
        }
        failure {
            echo '❌ Update failed!'
        }
    }
}