pipeline{
    agent { label 'GameOfLife-node2' }
    tools{
        jdk 'JDK_8'
        maven 'MAVEN_3.6.3'
    }
    triggers {
        pollSCM('* * * * *')
    }
    parameters{
        choice(name: 'GOAL', choices: ['clean package', 'package', 'clean install', 'clean package install'], description: 'This is maven goal')
    }
    stages{
        stage('Checkout code'){
            steps{
                git url: 'https://github.com/mk2502dev/game-of-life-july23.git',
                    branch: 'develop'
            }
        }
        stage('Building'){
            steps{
                sh script: "mvn ${params.GOAL}"
            }
        }
        stage('Archive the artifacts'){
            steps{
                archiveArtifacts artifacts: '**/gameoflife.war'
            }
        }
        stage('JUnit Report'){
            steps{
                junit testResults: '**/surefire-reports/TEST-*.xml'
            }
        }
    }
    post{
        success{
            mail subject: "${JOB_NAME}:: Build successfully!!",
                 body: "Congratulations your project build successfully without any error ${BUILD_URL}",
                 to: 'mbrdi@mercedes-benz.com'
        }
        failure{
            mail subject: "${JOB_NAME}:: Build Fails!!",
                 body: "Sorry your project build failed please see the logs ${JOB_URL}${BUILD_NUMBER}/console",
                 to: 'mbrdi@mercedes-benz.com'
        }
    }
     
}