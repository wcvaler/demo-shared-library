package shared;

import org.jenkinsci.plugins.docker.workflow.*;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.security.ACL;
import groovy.json.JsonSlurper
 
class JenkinsZceeUtil extends BaseUtil implements Serializable {

protected script
protected String artefactoryUrl
protected String buildTimestamp
protected String branchName
protected String buildUser;
protected String buildUserId;
protected String version;
public JenkinsZceeUtil(script){
this.script=script
this.script.env.artifactoryUrl
this.branchName=this.script.env.BRANCH_NAME

}

public void prepare(){
    setBuildTimestamp()
    setBuildUserAndBuildUserId()
    readCurrentVersion()
  }

  protected void promoteRepository(String applicationName, String releaseTagName, String repoSuffix=""){
    def environment = "CERT"
    def repositoryURL = "${artefactoryUrl}"
    def currentDate = buildTimestamp
/*
    if(!"${branchName}".startsWith("tags/RC-")){
      this.throwException("The release must be done on release")
    }*/
    def buildNumber = "${releaseTagName}"
    def buildName = "${this.script.env.project}".toLowerCase()+"-${applicationName}"
    def sourceRepo = "${this.script.env.project}${repoSuffix}-${environment}"
    def targetRepo = "${this.script.env.project}${repoSuffix}.Release"
    def ciName = "jks-${buildUserId}"
    def actionURL="${repositoryURL}/api/build/promote/${buildName}/${buildNumber}"
    def timestamp = this.script.steps.sh(script: "echo \$(date +\"%Y-%m-%dT%H:%M:%S.%3N%z\")", returnStdout: true).trim()
    def gitlabCommentPrefix="[ci-${releaseTagName}]"
    def stateComment="Promote-release"
    def comment="${gitlabCommentPrefix} ${stateComment} \nbranch: ${branchName}\ncommit: ${this.script.env.GIT_COMMIT}\ndate: ${currentDate}\nbuild-url: ${script.env.BUILD_URL}\nbuild-user-id: ${buildUserId}\nbuild-user: ${buildUser}"
    this.printMessage("comment: ${comment}")
    def errorArtifactory
    def errorMessageArtifactory

    //def artifactoryPromoteJsonDataRequest = new File("${script.env.WORKSPACE}/artifactory-json.json")
    def jsonData = this.getArtifactoryPromoteJsonDataRequest(environment,comment,ciName,timestamp,sourceRepo,targetRepo)
    this.printMessage("artifactoryBuildInfoJson: ${jsonData}")
    //artifactoryPromoteJsonDataRequest.append(jsonData)
    this.script.steps.writeFile(
      file:"${this.script.env.WORKSPACE}/artifactory-json.json",
      text: jsonData
    )

    def projectName = "${this.script.env.project}"
    def projectNameLowercase = projectName.toLowerCase()
    /*this.docker.withServer("${this.script.env.DOCKER_HOST}"){
      this.docker.withRegistry("http://${this.script.env.DOCKER_REGISTRY_URL}") {
        this.script.steps.withCredentials([[
                                             $class: 'UsernamePasswordMultiBinding',
                                             credentialsId: projectNameLowercase+'-jenkins-artefactory-token',
                                             usernameVariable: 'ARTIFACT_USER_NAME',
                                             passwordVariable: 'ARTIFACT_USER_PASSWORD'
                                           ]]) {
          def dockerVolumen = "-v '${this.script.env.WORKSPACE}:/project/data' -v ${this.script.env.APPLICATION_TOOL}/oracle_java/jdk1.8.0_172/jre/lib/security/cacerts:/usr/lib/jvm/jdk1.8.0_172/jre/lib/security/cacerts -w /project/data "
          def dockerParameters = "--network=host --cpus 2"
          def dockerCmd = "docker run --rm ${dockerVolumen} ${dockerParameters} ${this.dockerMavenjavaEnvironment}";
          def executionCommand = "curl -o response.txt -u ${script.env.ARTIFACT_USER_NAME}:${script.env.ARTIFACT_USER_PASSWORD} -H \"Content-Type: application/json\" --data @artifactory-json.json ${actionURL}"
          this.script.steps.sh "${dockerCmd} ${executionCommand}"

          def callback = this.script.steps.sh(script: "cat response.txt", returnStdout: true).trim()
          this.printMessage("Callback: ${callback}")

          errorArtifactory = this.script.steps.sh (
            script:  "cat response.txt | jq --raw-output '.messages | .[0] | .level'",
            returnStdout: true
          ).trim()

          //Request has error
          if(errorArtifactory=="ERROR"){
            errorMessageArtifactory = this.script.steps.sh (
              script: "cat response.txt | jq --raw-output '.messages | .[0] | .message'",
              returnStdout: true
            ).trim()
            this.throwException("Error message: ${errorMessageArtifactory}")
          }

          errorArtifactory = this.script.steps.sh (
            script:  "cat response.txt | jq --raw-output '.errors | .[0] | .status'",
            returnStdout: true
          ).trim()
          errorMessageArtifactory = this.script.steps.sh (
            script:  "cat response.txt | jq --raw-output '.errors | .[0] | .message'",
            returnStdout: true
          ).trim()

          //Artefact has promoted or moved
          if(errorMessageArtifactory!="null"){
            this.throwException("Error message: ${errorMessageArtifactory}")
          }
        }
      }
    }*/
  }

protected void buildZconnect(projectDirectory){

this.script.steps.sh("docker run -v \$(pwd):/workspace radioidol/zconbt:1.0.0 -pd=workspace/${projectDirectory} -od=/workspace ")

}

protected String getArtifactoryPromoteJsonDataRequest(
    String environment,
    String comment,
    String ciName,
    String timestamp,
    String sourceRepo,
    String targetRepo
  ){
    def initScript = """
{
 "status": "${environment}-Promoted",
 "comment" : "${comment}",
 "ciUser": "${ciName}",
 "timestamp" : "${timestamp}",
 "dryRun" : false,
 "sourceRepo" : "${sourceRepo}",
 "targetRepo" : "${targetRepo}",
 "copy": true,
 "artifacts" : true,
 "dependencies" : false,
 "scopes" : [ "compile", "runtime" ],
 "failFast": true
}""".stripIndent()
    return initScript
  }

protected void setBuildTimestamp() {
    this.buildTimestamp = this.script.steps.sh(
      script: "date '+%Y%m%d%H%M%S'",
      returnStdout: true
    ).trim();
    this.printMessage("BuildTimestamp: ${buildTimestamp}")
  }


  protected void uploadSnapshot(project,type){
    def artifactName="${project.toLowerCase()}"
    printMessage("artifact name ${artifactName}")
    def artifactoryUrlRepo="${this.script.env.artifactoryUrl}/generic-local/zosconnect/"

    this.script.steps.sh( "curl -u admin:db2admin -X PUT ${artifactoryUrlRepo}${artifactName}-${this.version}-SNAPSHOT.${type} -T ${artifactName}.${type}")
  }

protected void setBuildUserAndBuildUserId() {
   // this.script.steps.wrap([$class: 'BuildUser']) {
      def buildUser = "${this.script.env.BUILD_USER}";
      def buildUserId = "${this.script.env.BUILD_USER_ID}";
      this.buildUser = buildUser;
      printMessage("Usuario de build: ${this.buildUser}")
      this.buildUserId = buildUserId;
      printMessage("Id usuario de build: ${this.buildUserId}")
   // }
  }

protected void printMessage(String message){
    this.script.steps.echo "[BCP-DevSecOps] ${message}"
  }

protected void readCurrentVersion(){
 this.version  = (this.script.steps.readFile('version').trim())
 printMessage("version actual es ${version}")
}
 

 protected void startRC(){
   
 }

}