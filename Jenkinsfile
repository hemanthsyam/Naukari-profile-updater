pipeline {
    agent any

    environment {
        // Credentials from Jenkins Credentials Manager
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'
    }

    stages {
        stage('Checkout Code') {
            steps {
                echo '📥 Checking out code...'
                checkout scm

                // List files to verify
                bat 'dir /s *.java'
            }
        }

        stage('Download Dependencies') {
            steps {
                echo '📦 Downloading required JAR files...'
                bat """
                    echo "Creating libs directory..."
                    mkdir libs 2>nul
                    cd libs

                    echo "Downloading Selenium..."
                    curl -L -o selenium.jar "https://repo1.maven.org/maven2/org/seleniumhq/selenium/selenium-java/4.15.0/selenium-java-4.15.0.jar"

                    echo "Downloading ChromeDriver manager..."
                    curl -L -o webdrivermanager.jar "https://repo1.maven.org/maven2/io/github/bonigarcia/webdrivermanager/5.6.2/webdrivermanager-5.6.2.jar"

                    echo "Downloading Gson..."
                    curl -L -o gson.jar "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"

                    echo "Downloading Commons IO..."
                    curl -L -o commons-io.jar "https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar"

                    cd ..
                    echo "Dependencies downloaded"
                """
            }
        }

        stage('Compile Java Code') {
            steps {
                echo '🔨 Compiling Java source code...'
                bat """
                    echo "Creating classes directory..."
                    mkdir target 2>nul
                    mkdir target\\classes 2>nul

                    echo "Compiling..."
                    javac -cp ".;libs\\*.jar" -d target\\classes src\\main\\java\\com\\automation\\*.java

                    if %errorlevel% neq 0 (
                        echo "Compilation failed!"
                        exit 1
                    )

                    echo "✅ Compilation successful"
                    dir target\\classes\\com\\automation\\*.class
                """
            }
        }

        stage('Run Naukri Automation') {
            steps {
                echo '🚀 Running Naukri Profile Updater...'
                bat """
                    echo "Setting up environment variables..."
                    set NAUKRI_USERNAME=%NAUKRI_USERNAME%
                    set NAUKRI_PASSWORD=%NAUKRI_PASSWORD%
                    set GEMINI_API_KEY=%GEMINI_API_KEY%
                    set HEADLESS_MODE=%HEADLESS_MODE%

                    echo "Running the automation..."
                    java -cp "target\\classes;libs\\*.jar" com.automation.NaukriProfileUpdater

                    echo "✅ Automation completed"
                """
            }
        }

        stage('Archive Results') {
            steps {
                echo '📦 Archiving results...'
                bat """
                    echo "Checking for output files..."
                    dir *.log
                    dir *.png

                    if exist *.log (
                        echo "Found log files"
                    ) else (
                        echo "No log files found"
                    )
                """
            }
        }
    }

    post {
        always {
            echo "📊 Build completed with status: ${currentBuild.currentResult}"
            archiveArtifacts artifacts: '*.log, *.txt, *.png', allowEmptyArchive: true
        }

        success {
            echo '🎉 SUCCESS: Naukri profile updated successfully!'
            bat 'echo "✅ Profile updated at %DATE% %TIME%" >> success_report.txt'
        }

        failure {
            echo '❌ FAILURE: Check console for errors'
            bat 'echo "❌ Failed at %DATE% %TIME%" >> error_report.txt'
        }
    }
}