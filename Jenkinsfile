pipeline {
    agent any

    // Schedule to run 4 times daily on weekdays (9 AM, 11 AM, 3 PM, 5 PM IST)
    triggers {
        cron('0 9,11,15,17 * * 1-5')
    }

    // Environment variables
    environment {
        // Tools configuration - MUST match names in Jenkins → Tools
        JAVA_HOME = "${tool 'JDK11'}"
        MAVEN_HOME = "${tool 'Maven 3.9.12'}"
        PATH = "${env.JAVA_HOME}/bin;${env.MAVEN_HOME}/bin;${env.PATH}"

        // Credentials from Jenkins Credentials Manager
        NAUKRI_USERNAME = credentials('naukri-username')
        NAUKRI_PASSWORD = credentials('naukri-password')
        GEMINI_API_KEY = credentials('gemini-api-key')
        HEADLESS_MODE = 'true'

        // Project variables
        PROJECT_NAME = 'Naukri-Daily-Updater'
        LOG_FILE = "naukri_update_${BUILD_NUMBER}.log"
    }

    // Options for the entire pipeline
    options {
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        retry(2)
    }

    // Pipeline stages
    stages {

        // Stage 1: Pre-Cleanup and Setup
        stage('Preparation') {
            steps {
                echo '🧹 Cleaning workspace...'
                cleanWs()

                echo '🔧 Setting up environment...'
                bat """
                    echo "Java Home: %JAVA_HOME%"
                    echo "Maven Home: %MAVEN_HOME%"
                    echo "Build Number: %BUILD_NUMBER%"
                    echo "Workspace: %WORKSPACE%"
                    java -version
                    mvn --version
                """
            }
        }

        // Stage 2: Checkout Code
        stage('Checkout Code') {
            steps {
                echo '📥 Checking out source code from GitHub...'
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: '*/main']],
                    userRemoteConfigs: [[
                        url: 'https://github.com/hemanthsyam/Naukari-profile-updater.git',
                        credentialsId: ''  // Add if private repo
                    ]],
                    extensions: [[
                        $class: 'CleanCheckout',
                        deleteUntrackedNestedRepositories: true
                    ]]
                ])

                bat 'dir /s pom.xml'
            }
        }

        // Stage 3: Validate Project Structure
        stage('Validate Project') {
            steps {
                echo '🔍 Validating project structure...'
                script {
                    def files = findFiles(glob: '**/pom.xml')
                    if (files.isEmpty()) {
                        error('pom.xml not found! Check project structure.')
                    }
                    echo "Found pom.xml at: ${files[0].path}"
                }

                bat """
                    echo "Checking project files..."
                    if exist pom.xml (
                        echo "✅ pom.xml exists"
                    ) else (
                        echo "❌ pom.xml missing"
                        exit 1
                    )

                    if exist src (
                        echo "✅ src directory exists"
                    ) else (
                        echo "❌ src directory missing"
                        exit 1
                    )
                """
            }
        }

        // Stage 4: Build Project
        stage('Build') {
            steps {
                echo '🔨 Building project with Maven...'
                bat """
                    mvn clean compile
                    echo "Build completed successfully"
                """
            }

            post {
                success {
                    echo '✅ Build successful!'
                    archiveArtifacts artifacts: 'target/classes/**/*', allowEmptyArchive: true
                }
                failure {
                    echo '❌ Build failed!'
                    bat 'mvn clean'  // Clean up failed build
                }
            }
        }

        // Stage 5: Run Naukri Automation
        stage('Execute Naukri Update') {
            steps {
                echo '🚀 Executing Naukri profile update...'
                script {
                    // Try with retry logic
                    retry(3) {
                        bat """
                            echo "Starting automation with:"
                            echo "Username: %NAUKRI_USERNAME%"
                            echo "API Key configured: %GEMINI_API_KEY:~0,20%..."

                            mvn exec:java -Dexec.mainClass="com.automation.NaukriProfileUpdater"

                            echo "Automation completed"
                        """
                    }
                }
            }

            post {
                success {
                    echo '✅ Naukri profile updated successfully!'
                    // Take screenshot if available
                    bat 'if exist *.png copy *.png %LOG_FILE%.screenshots 2>nul || echo No screenshots found'
                }
                failure {
                    echo '❌ Naukri update failed!'
                    // Capture error logs
                    bat 'if exist *.log copy *.log %LOG_FILE%.error 2>nul'
                }
            }
        }

        // Stage 6: Post-Execution Cleanup
        stage('Cleanup') {
            steps {
                echo '🧹 Cleaning up temporary files...'
                bat """
                    echo "Cleaning temporary files..."
                    del /q *.png 2>nul || echo No PNG files to delete
                    del /q *.tmp 2>nul || echo No temp files to delete

                    echo "Moving logs..."
                    if exist *.log (
                        copy *.log "%LOG_FILE%" 2>nul
                        echo "Logs saved to %LOG_FILE%"
                    )
                """
            }
        }
    }

    // Post-build actions
    post {
        always {
            echo '📊 Build completed with status: ${currentBuild.currentResult}'

            // Archive important files
            archiveArtifacts artifacts: '*.log, *.txt', allowEmptyArchive: true

            // Clean workspace except logs
            bat 'del /q /s *.class *.jar 2>nul || echo No class/jar files to clean'
        }

        success {
            echo '🎉 SUCCESS: Naukri profile has been updated!'
            script {
                // Simple success notification (email optional)
                if (env.BUILD_NUMBER.toInteger() % 5 == 0) {
                    echo "📧 Would send success email (every 5th build)"
                    // emailext (
                    //     subject: "✅ Naukri Update Success - Build #${BUILD_NUMBER}",
                    //     body: "Profile updated successfully at ${new Date()}",
                    //     to: 'hemanthkumarmasarapadi@gmail.com'
                    // )
                }
            }
        }

        failure {
            echo '💥 FAILURE: Check logs for details'
            script {
                // Capture last 50 lines of console for debugging
                def consoleLog = currentBuild.rawBuild.getLog(50).join('\n')
                writeFile file: 'build_failure.log', text: consoleLog
                archiveArtifacts artifacts: 'build_failure.log'

                echo "Last error lines:\n${consoleLog}"
            }
        }

        unstable {
            echo '⚠️ UNSTABLE: Tests or checks failed'
        }

        changed {
            echo '🔄 Build status changed from previous'
        }
    }
}