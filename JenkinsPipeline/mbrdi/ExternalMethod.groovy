
def kafkaConnectors(command,cluster,String[] arr) {
    dir = "/opt/pai/application/lenses-cli"
    sh "cd $dir"
    echo "$command"
    //sh "sudo /opt/pai/application/kafka_connectors/lenses-cli-linux-amd64-3.0.8/lenses-cli connectors --cluster-name='TEST' --output json"
    for (int i=0; i<arr.length; i++)
	{
    	sh "sudo $dir/lenses-cli connector '$command' --cluster-name='$cluster' --name=${arr[i]}"
	}
}
def downloadFromJfrog(String repoPath,String deliveryPath,String fileName,String USERNAME,String PASSWORD) {
    echo "From ExternalMethod $PASSWORD"
	def status = sh script:"curl -I ${repoPath}/${fileName} -u ${USERNAME}:${PASSWORD}", returnStdout:true
    echo status
    if(status.find("200")) {
        sh "cd $deliveryPath && curl -O ${repoPath}/${fileName} -u ${USERNAME}:${PASSWORD}"
        stash 'jenkins-deployment-pipeline'
        echo "Delivery downloaded to the master successful!!"
    }
    else
        failBuild("Download failed!!check the URL status code.")
}
def validateInputParams(String param) {
    echo "$param"
    if (param == null || param.length() == 0) {
        abortBuild("Delivery paramater must be set correctly!")
    }
    echo "Validation successful!!"
}
def sendEmail(String subject, String emailto,String emailcc, String appName, boolean attachLog) {

    //Use emailext function to send e-mail attachmentsPattern: '*.log'
    emailext (to: emailto, cc: emailcc ,attachLog: attachLog,

    //Include environment variable to determine your job and the current job number
    subject: "$subject",
    mimeType: 'text/html; charset=UTF-8',
    body: 'Dear ' +appName+ ' team,<br><br>'
    //Add URL to directly access the Build
    + '<br> More info at: ' + env.BUILD_URL + '<br>'
    + '<br><br>Regards<br> Jenkins')
}
def callInstallerCommands(username, password, appName, command) {
    def fileName=appName+Version
    dir = fileName
    buildCommand = "cd $dir/app/tools && sudo /opt/pai/IBM/WebSphere/Profiles/dmgr/bin/wsadmin.sh -lang jython -f ACMSetup.py -user $username -password $password $command -nonInteractive=1 | tee -a $WORKSPACE/$dir"+".log"
    echo "callInstallerCommands calling executeCommand"
    executeCommand(buildCommand,dir)
}
def executeCommand(command,appName) {
    dir = appName
    echo "executeCommand executing"
    if(command.matches(/.*\sinstall.*/)) {
        def installCommand = "$command | tee -a $WORKSPACE/installation"+".log"
        sh  "$installCommand"
        echo "In executeCommand() method : install command executing"
        readFile("$WORKSPACE/installation"+".log").split("\n").each{ line ->
            if(line =~ /.*installation\sfinished.*/) {
                echo "Command executed successfully"
            }else {
                               //[eE]rror regex removed
                regPatterns = [/SqlTransactionRollbackException/, /WASX7017E/,/\b\w*[Ee](rror|xception)$/]
                for(item in regPatterns){
                    if (line =~ item){
                        echo "Exception or Error found in command output : $line"
                        failBuild("Installing stage failed!")
                    }
                }
            }
        }
    } else {
        echo "Single sh command called $command"
        int status = sh script:command, returnStatus:true
        if(status!=0) {
            error "$command"+ " execution failed!"
        } else {
            echo "$command"+ " successful"
        }
    }
}
def failBuild(String message) {
    currentBuild.result='FAILURE'
    error message
}
def abortBuild(String message) {
    currentBuild.result='ABORTED'
    error message
}
return this;