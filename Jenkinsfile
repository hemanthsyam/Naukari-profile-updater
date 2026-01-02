pipeline {
    agent any

    environment {
        // Credentials
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'

        // Manual paths (update these to match your system)
        JAVA_HOME = "C:\\Program Files\\Java\\jdk-25"
    }

    stages {
        stage('Install Maven') {
            steps {
                bat """
                    echo "=== INSTALLING MAVEN ==="

                    # Check if Maven already exists
                    where mvn 2>nul && (
                        echo "Maven already installed"
                        goto :maven_installed
                    )

                    echo "Downloading Maven 3.9.12..."

                    # Download Maven using PowerShell
                    powershell -Command "& {
                        \$url = 'https://dlcdn.apache.org/maven/maven-3/3.9.12/binaries/apache-maven-3.9.12-bin.zip'
                        \$output = 'maven.zip'
                        Invoke-WebRequest -Uri \$url -OutFile \$output

                        # Extract
                        Expand-Archive -Path \$output -DestinationPath '.' -Force
                    }"

                    # Set up Maven
                    set MAVEN_HOME=%WORKSPACE%\\apache-maven-3.9.12
                    set PATH=%MAVEN_HOME%\\bin;%PATH%;%JAVA_HOME%\\bin

                    :maven_installed
                    echo "Verifying installation..."
                    mvn --version
                """
            }
        }

        stage('Checkout Code') {
            steps {
                echo '📥 Checking out code...'
                checkout scm

                bat 'dir'
            }
        }

        stage('Build Project') {
            steps {
                bat """
                    echo "=== BUILDING PROJECT ==="
                    echo "Current directory: %CD%"

                    # Ensure paths are set
                    set PATH=%WORKSPACE%\\apache-maven-3.9.12\\bin;%PATH%;%JAVA_HOME%\\bin

                    # Build
                    mvn clean compile

                    if %errorlevel% neq 0 (
                        echo "Build failed with exit code: %errorlevel%"
                        exit 1
                    )

                    echo "✅ Build successful"
                """
            }
        }

        stage('Run Naukri Automation') {
            steps {
                bat """
                    echo "=== RUNNING NAUKRI UPDATER ==="

                    # Run your application
                    mvn exec:java -Dexec.mainClass="com.automation.NaukriProfileUpdater"

                    echo "✅ Automation completed"
                """
            }
        }
    }

    post {
        always {
            echo "📊 Build status: ${currentBuild.currentResult}"
            archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
        }
        success {
            echo '🎉 SUCCESS: Profile updated!'
            bat 'echo Success at %DATE% %TIME% >> success.txt'
            archiveArtifacts artifacts: 'success.txt'
        }
        failure {
            echo '❌ FAILURE: Check logs above'
            bat 'echo Failed at %DATE% %TIME% >> failure.txt'
            archiveArtifacts artifacts: 'failure.txt'
        }
    }
}