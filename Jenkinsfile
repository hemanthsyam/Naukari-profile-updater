pipeline {
    agent any

    environment {
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'
    }

    stages {
        stage('Run Directly') {
            steps {
                bat """
                    echo "Running Naukri updater directly..."

                    # Set Java path
                    set JAVA_HOME=C:\\Program Files\\Java\\jdk-25
                    set PATH=%JAVA_HOME%\\bin;%PATH%

                    # Navigate to project
                    cd "%WORKSPACE%"

                    # Compile if needed
                    if not exist "target\\classes" (
                        echo "Compiling..."
                        javac -d target/classes -cp ".;libs\\*" src\\main\\java\\com\\automation\\*.java
                    )

                    # Run the application
                    java -cp "target/classes;libs\\*" com.automation.NaukriProfileUpdater
                """
            }
        }
    }
}