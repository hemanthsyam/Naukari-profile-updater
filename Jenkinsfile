pipeline {
    agent any

    environment {
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
    }

    stages {
        stage('Run') {
            steps {
                script {
                    // Run a simple test first
                    bat 'java -version'

                    // Then try to run your app directly
                    bat """
                        cd "%WORKSPACE%"
                        echo "Running from: %CD%"
                        dir

                        REM Try to compile and run
                        javac -version 2>&1
                        echo "If you see javac above, Java is working"
                    """
                }
            }
        }
    }
}