pipeline {
    agent any

    tools {
        // This tells Jenkins to use the Maven & JDK we configured in Step 4
        maven 'maven-3.9' 
        jdk 'jdk-17'
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
                // This will now use the correct git.exe
                checkout scm 
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
}
