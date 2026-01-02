pipeline {
    agent any

    triggers {
        // Run at 9 AM, 11 AM, 3 PM, 5 PM IST (Monday to Friday)
        cron('0 9,11,15,17 * * 1-5')
    }

    environment {
        JAVA_HOME = tool 'JDK11'
        MAVEN_HOME = tool 'Maven3'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '📦 Checking out code...'
                // If using Git, checkout will happen automatically
                // If local, files are already present
            }
        }

        stage('Build') {
            steps {
                echo '🔨 Building project...'
                bat 'mvn clean compile' // Use 'sh' instead of 'bat' on Linux/Mac
            }
        }

        stage('Run Naukri Update') {
            steps {
                echo '🚀 Starting Naukri profile update...'
                bat 'mvn exec:java -Dexec.mainClass="com.naukri.automation.NaukriProfileUpdater"'
                // Use 'sh' instead of 'bat' on Linux/Mac
            }
        }
    }

    post {
        success {
            echo '✅ Profile updated successfully!'
            emailext (
                subject: "✅ Naukri Profile Updated - ${new Date().format('dd-MM-yyyy HH:mm')}",
                body: """
                    <h3>Naukri Profile Update Successful</h3>
                    <p><b>Time:</b> ${new Date()}</p>
                    <p><b>Build Number:</b> #${BUILD_NUMBER}</p>
                    <p>Your profile has been optimized with AI-generated keywords.</p>
                """,
                to: 'hemanthkumarmasarapadi@gmail.com',
                mimeType: 'text/html'
            )
        }

        failure {
            echo '❌ Profile update failed!'
            emailext (
                subject: "❌ Naukri Update Failed - ${new Date().format('dd-MM-yyyy HH:mm')}",
                body: """
                    <h3>Naukri Profile Update Failed</h3>
                    <p><b>Time:</b> ${new Date()}</p>
                    <p><b>Build Number:</b> #${BUILD_NUMBER}</p>
                    <p><a href="${BUILD_URL}console">View Console Output</a></p>
                """,
                to: 'hemanthkumarmasarapadi@gmail.com',
                mimeType: 'text/html'
            )
        }

        always {
            echo '📝 Archiving logs...'
            archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
        }
    }
}