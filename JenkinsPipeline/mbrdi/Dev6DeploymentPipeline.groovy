def appName = "acm-delivery-"
def applicationName = "ACM"
def enviromentName = 'DEV6'
def websphereCredentialId = 'wasuserDEV6'
def jfrogCredentialId = 'adaikas'
def emailTo = 'dw_623-MBRDI-Proj_GIE-ACMDEV'
def emailCc = 'suresh_kumar.adaikalam_subramanian@mercedes-benz.com'
def repoPath = "https://artifacts.i.mercedes-benz.com/artifactory/acm-acm-maven-releases/com/daimler/acm/server/acm-delivery"
def backofficeStatusPage = 'https://acm-dev6.es.corpintra.net/acm-backoffice-war/status'
def onlineStatusPage = 'https://acm-dev6.es.corpintra.net/acm-online-war/status'

node('dot-runner-0') {
    checkout scm
    def externalMethod = load("ExternalMethod.groovy")
    def fileName = appName + Version + ".zip"
    echo "file name : $fileName"
    def delivery = params.Version
    echo "delivery - $delivery"
    stage('parameter validation') { externalMethod.validateInputParams(delivery) }
    stage('Notify Installation') {
        externalMethod.sendEmail("Starting deployment of ${applicationName} on  ${enviromentName}-Environment", emailTo, emailCc, applicationName, false)
    }
    stage('Download to dot-runner-0') {
	/*cleanWs()*/
	echo "Cleanup completed"
        def deliveryPath = "${WORKSPACE}/" + Version
        echo "Create Delivery Directory:$deliveryPath"
        if (!fileExists("$deliveryPath")) {
            externalMethod.executeCommand("mkdir $deliveryPath", "")
        }
        echo "Start Download of Delivery ${Version}:"
        withCredentials([usernamePassword(credentialsId: jfrogCredentialId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            externalMethod.downloadFromJfrog(repoPath, deliveryPath, fileName, USERNAME, PASSWORD)
        }
    }
}

node('smtcagdacm00') {
    echo "unstashing the delivery on slave"
    unstash 'jenkins-deployment-pipeline'
    def externalMethod = load("ExternalMethod.groovy")
    def fileName = appName + Version + ".zip"
    def deliveryPath = "${WORKSPACE}/" + Version
    def delivery = params.Version
    def command = "installACM -debug"
    try {
        stage('Upload to Slave') {
            echo "Create Delivery Directory on Slave"
            if (!fileExists("$deliveryPath")) {
                externalMethod.executeCommand("mkdir $deliveryPath", "")
            }
            echo "Check if file is in Delivery Folder"
            echo "deliverypath: $deliveryPath,filename:$fileName"
            if (fileExists("$deliveryPath/$fileName")) {
                echo "Upload successful!"
            } else {
                externalMethod.failBuild("Copy to Slave was not successfull.")
            }
        }
        stage('Extract archive') {
            echo fileName
            def dir = fileName.take(fileName.lastIndexOf('.'))
            echo dir
            if (fileExists("$WORKSPACE/$delivery/$fileName")) {
                echo "file exist on slave!"
            } else {
                externalMethod.failBuild("file doesn't exist!")
            }
            echo "Going to extract $WORKSPACE/$delivery/$fileName"
            externalMethod.executeCommand("unzip -o -q $WORKSPACE/$delivery/$fileName", "")
            externalMethod.executeCommand("cd $dir/app/tools", "")
        }
        withCredentials([usernamePassword(credentialsId: websphereCredentialId, passwordVariable: 'wasadminPW', usernameVariable: 'wasadmin')]) {
            echo "In withCredentials"
			stage('Stopping Servers') {
                echo "In Stopping Servers"
				externalMethod.callInstallerCommands(wasadmin, wasadminPW, appName, "stopServers waitForServerShutdown")
            }
            stage('Installing ' + applicationName) {
                externalMethod.callInstallerCommands(wasadmin, wasadminPW, appName, command)
            }
            stage('Starting Servers') {
                echo "In Starting Servers"
				externalMethod.callInstallerCommands(wasadmin, wasadminPW, appName, "startServers waitForServerStartup")
            }
        }
        stage('Server Status') {
            def backofficeStatus = sh script: "curl -I ${backofficeStatusPage}", returnStdout: true
            def onlineStatus = sh script: "curl -I ${onlineStatusPage}", returnStdout: true
            def count =0;
            if (backofficeStatus.find("200") && onlineStatus.find("200")) {
                echo "all the servers are up and running"
            }
            else if(!backofficeStatus.find("200") && !onlineStatus.find("200") && count < 10){
                backofficeStatus = sh script: "curl -I ${backofficeStatusPage}", returnStdout: true
                onlineStatus = sh script: "curl -I ${onlineStatusPage}", returnStdout: true
                echo "waiting till all the servers are up: $count"
                count++;
            }
            else {
                echo "servers are not started on time, giving up! please check the logs"
            }
        }
        stage('Archive Artifacts') {
            archiveArtifacts artifacts: '*.log', fingerprint: true
        }
    }
    catch (exc) {
        externalMethod.failBuild('Build failed!!!please check the build logs')
    }
    finally {
        externalMethod.sendEmail("Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}: ${currentBuild.currentResult}", emailTo, emailCc, applicationName, true)
        stage('cleaup Workspace') {
            echo "cleaning up the workspace"
            /* clean up the workspace */
	    cleanWs()
            deleteDir()		
        }
    }
}