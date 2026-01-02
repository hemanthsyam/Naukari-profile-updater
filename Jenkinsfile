pipeline {
    agent any

    environment {
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'
    }

    stages {
        // "Install Maven" stage is removed because it is now installed on the OS

        stage('Checkout and Build') {
            steps {
                checkout scm
                bat """
                    echo "Building with Maven..."
                    mvn clean compile
                """
            }
        }

        stage('Run Automation') {
            steps {
                bat """
                    echo "Running Naukri updater..."
                    mvn exec:java -Dexec.mainClass="com.automation.NaukriProfileUpdater"
                """
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
        }
    }
}