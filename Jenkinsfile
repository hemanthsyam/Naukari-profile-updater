pipeline {
    agent any

    environment {
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'
    }

    stages {
        stage('Install Maven') {
            steps {
                echo '📦 Installing Maven...'
                bat """
                    echo "Checking for Maven..."
                    where mvn >nul 2>nul
                    if %errorlevel% neq 0 (
                        echo "Maven not found, installing..."

                        REM Download Maven
                        curl -L -o maven.zip "https://dlcdn.apache.org/maven/maven-3/3.9.12/binaries/apache-maven-3.9.12-bin.zip"

                        REM Extract
                        powershell -Command "Expand-Archive -Path 'maven.zip' -DestinationPath 'maven' -Force"

                        set MAVEN_HOME=%CD%\\maven\\apache-maven-3.9.12
                        set PATH=%MAVEN_HOME%\\bin;%PATH%

                        echo "Maven installed at: %MAVEN_HOME%"
                    ) else (
                        echo "Maven already installed"
                    )

                    mvn --version
                """
            }
        }

        stage('Checkout and Build') {
            steps {
                checkout scm

                bat """
                    echo "Building with Maven..."
                    mvn clean compile

                    if %errorlevel% neq 0 (
                        echo "Build failed!"
                        exit 1
                    )

                    echo "✅ Build successful"
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