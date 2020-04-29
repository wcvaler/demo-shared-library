package shared
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper;
import org.jenkinsci.plugins.docker.workflow.*;
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*

class BaseUtil {
  protected boolean fortifyActivated = true
  protected static final String execFortifyCpu = "1"
  protected static final String execFortifyMemory = "-Xmx20G"
  protected static final String dockerMaven339java8Version="bcp/maven339-java180_172:1.0"
  protected static final String dockerMavenjavaEnvironment="${dockerMaven339java8Version}"
  protected static final String dockerPython27GoogleApiEnviroment ="bcp/python27-google-api-python-client:1.2"
  protected static final String dockerFortifyRunner = "bcp/fortify1920-maven339-java180_172:1.0"
  protected script;
  protected String type;
  protected String sonarqubeUrl;
  protected String gitlabUrl;
  protected String bitbucketUrl;
  protected def buildTimestamp
  protected String branchName;
  protected String currentGitCommit;
  protected String currentGitURL ;
  protected String buildUserMail;
  protected String buildUser;
  protected String buildUserId;
  protected String fortifyUrl
  protected String fortifyHost
  protected def currentCredentialsId
  protected Docker docker
  protected String artefactoryUrl
  protected boolean xrayActivated=false;
  protected String repositoryURL
  protected String artifactM2RegistryUrl

  protected BaseUtil(){}

  protected BaseUtil(script, String type = '') {
    this.script = script
    this.type = type

    this.gitlabUrl = "${script.env.GITLAB_PROTOCOL}://${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}";
    this.bitbucketUrl = this.gitlabUrl;
    this.fortifyUrl = "${script.env.FORTIFY_SCC_URL}"
    this.fortifyHost = "${script.env.FORTIFY_SCC_HOST}"
    def remoteConfigs = this.script.scm.getUserRemoteConfigs()
    for (Object remoteConfig : remoteConfigs) {
      this.currentCredentialsId = remoteConfig.getCredentialsId();
      this.script.steps.echo "[BCP-DevSecOps] CurrentCredentialsId para ${remoteConfig} --> ${currentCredentialsId}";
    }
    this.docker = new Docker(this.script);
    this.artefactoryUrl = this.script.env.ARTIFACT_SERVER_URL;
    this.repositoryURL = this.artefactoryUrl
    this.artifactM2RegistryUrl = "${script.env.ARTIFACT_SERVER_URL}/public/"
    script.steps.echo "BASE CONSTRUCT ${this.artifactM2RegistryUrl}"
    script.steps.echo "BASE CONSTRUCT ${this.artefactoryUrl}"
    script.steps.echo "BASE CONSTRUCT ${this.repositoryURL}"
  }

  public void prepare(){
    this.sonarqubeUrl = "${this.script.env.SONARQUBE_URL}"
    setBuildTimestamp()
    setBranchName()
    setCurrentGitCommit()
    setCurrentGitURL()
    setBuildUserMail()
    setBuildUserAndBuildUserId()
  }

  protected void setBuildUserAndBuildUserId() {
    this.script.steps.wrap([$class: 'BuildUser']) {
      def buildUser = "${this.script.env.BUILD_USER}";
      def buildUserId = "${this.script.env.BUILD_USER_ID}";
      this.buildUser = buildUser;
      printMessage("Usuario de build: ${this.buildUser}")
      this.buildUserId = buildUserId;
      printMessage("Id usuario de build: ${this.buildUserId}")
    }
  }

  protected void setBuildUserMail() {
    if (this.type == 'ligthweight') {
      def commitUserName = this.script.steps.sh(
        script: "git show --format=\"%aN\" ${this.currentGitCommit} | head -n 1",
        returnStdout: true
      ).trim();
      printMessage("Nombre usuario: ${commitUserName}")
      this.buildUserMail = this.script.steps.sh(
        script: "git show --format=\"%aE\" ${this.currentGitCommit} | head -n 1",
        returnStdout: true
      ).trim()
      this.printMessage("Correo usuaro: ${this.buildUserMail}")
    }
  }

  protected void setCurrentGitURL() {
    this.currentGitURL = this.script.steps.sh(
      script: 'git config --get remote.origin.url',
      returnStdout: true
    ).trim();
    this.printMessage("Actual url de git: ${currentGitURL}")
  }

  protected void setCurrentGitCommit() {
    this.currentGitCommit = this.script.steps.sh(
      script: 'git rev-parse HEAD',
      returnStdout: true
    ).trim();
    this.printMessage("Actual hash commit de git: ${currentGitCommit}")
  }

  protected void setBranchName() {
    this.branchName = "${this.script.env.gitlabSourceBranch}"
    this.branchName = this.script.steps.sh(
      script: 'git name-rev --name-only HEAD | sed "s?.*remotes/origin/??"',
      returnStdout: true
    ).trim();
    this.printMessage("Nombre de la rama: ${branchName}")
  }

  protected void setBuildTimestamp() {
    this.buildTimestamp = this.script.steps.sh(
      script: "date '+%Y%m%d%H%M%S'",
      returnStdout: true
    ).trim();
    this.printMessage("BuildTimestamp: ${buildTimestamp}")
  }

  protected void printMessage(String message){
    this.script.steps.echo "[BCP-DevSecOps] ${message}"
  }

  protected throwException(String message) {
    throw new Exception("[BCP-DevSecOps] ${message}");
  }

  public String getGitProjectName(){
    return this.script.steps.sh(
      script:"echo '${this.currentGitURL}' | cut -d '/' -f6 | sed 's/\\.git//g' ",
      returnStdout: true
    ).trim();
  }

  /*
  * This method notify by mail the status of Job
  */
  public void notifyByMail(String status = 'STARTED',String recipients) {
    // status of null means successful
    status =  status ?: 'SUCCESSFUL'

    // Default values
    def subject = "${status}: Job '${this.script.env.JOB_NAME} [${this.script.env.BUILD_NUMBER}]'"
    def details = """<p>${status}: Job '${this.script.env.JOB_NAME} [${this.script.env.BUILD_NUMBER}]':</p>
      <p>Check console output at &QUOT;<a href='${this.script.env.BUILD_URL}'>${this.script.env.JOB_NAME} [${this.script.env.BUILD_NUMBER}]</a>&QUOT;</p>"""

    if(recipients!=null&&!recipients.empty){
      // Send notifications to specific recipients
      if(this.type=='ligthweight'){
        if(recipients.length()>0){
          recipients+=","
        }
        recipients+="${buildUserMail}"
        this.printMessage("Recipients: ${recipients}")
      }
      this.script.steps.emailext (
        subject: subject,
        body: details,
        to: recipients,
        attachLog: false
      )
    }else{
      if(this.type=='ligthweight'){
        // Send notifications to specific recipients
        if(buildUserMail != null && !buildUserMail.empty){
          this.script.steps.emailext (
            subject: subject,
            body: details,
            to: "${buildUserMail}",
            attachLog: false
          )
        }
      }else{
        // Send notifications to launch job person
        this.script.steps.emailext (
          subject: subject,
          body: details,
          recipientProviders: [[$class: 'DevelopersRecipientProvider']],
          attachLog: false
        )
      }
    }
  }

  /*
  * This method will perform common post execution task
  */
  public void executePostExecutionTasks() {
    //Clean up workspace when job was executed ok, this improve performance on server
    this.script.steps.step([$class: 'WsCleanup', cleanWhenFailure: false])
    if(this.type=='ligthweight'){
      this.script.currentBuild.result = 'SUCCESS'
      if(currentGitCommit!=null){
        this.script.steps.notifyBitbucket commitSha1: "${currentGitCommit}"
      }
    }
  }

  /*
  * This method will perform common post execution task
  */
  public void executeOnErrorExecutionTasks() {
    //Clean up workspace when job was executed ok, this improve performance on server
    //TO FIX CLEAN UP
    if(this.type=='ligthweight'){
      this.script.currentBuild.result = 'FAILED'
      if(currentGitCommit!=null){
        this.script.steps.notifyBitbucket commitSha1: "${currentGitCommit}"
      }
    }
  }

  public void setFortifyEnabled(boolean fortifyActivatedParam){
   this.fortifyActivated = fortifyActivatedParam
  }

  protected void checkoutReleaseBranch(String version){
    String newReleaseBranch="release/${version}"
    checkoutBranch(newReleaseBranch)
  }

  protected checkoutBranch(String branchNameParam){
    checkoutRepositoryBranch(currentGitURL,branchNameParam)
  }

  protected checkoutRepositoryBranch(String gitURLParam,String branchNameParam){
    this.printMessage("Checking out: ${branchNameParam}")
    this.script.steps.step([$class: 'WsCleanup'])
    //steps.checkout script.scm
    this.script.steps.checkout([
      $class: 'GitSCM',
      branches: [[name: branchNameParam]],
      doGenerateSubmoduleConfigurations: false,
      extensions: [
        [$class: 'WipeWorkspace'], [$class: 'LocalBranch', localBranch: '**']
      ],
      submoduleCfg: [],
      userRemoteConfigs: [[credentialsId: currentCredentialsId, url: gitURLParam]]
    ])
    branchName = this.script.steps.sh (
      script: 'git name-rev --name-only HEAD | sed "s?.*remotes/origin/??"',
      returnStdout: true
    ).trim()
    currentGitCommit = this.script.steps.sh (
      script: 'git rev-parse HEAD',
      returnStdout: true
    ).trim()
    this.printMessage("branchName: ${branchName}")
    this.printMessage("currentGitCommit: ${currentGitCommit}")
  }

  protected void updateToReleaseVersion(String version){
    String releaseBranch="release/${version}"
    String repositoryPath=getRepositoryPath(gitlabUrl,currentGitURL)
    String currentDate = buildTimestamp

    this.printMessage("Current Git URL: ${currentGitURL}")
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      this.script.steps.sh "git status"
      this.script.steps.sh "git ls-files --modified | xargs git add"

      String gitlabCommentPrefix="[ci-${releaseBranch}]"
      String stateComment="Update-to-release-version: ${version}"
      String comment="${gitlabCommentPrefix} ${stateComment} \nbranch-source: ${branchName}\ncommit-source: ${currentGitCommit}\ndate: ${currentDate}\nbuild-url: ${script.env.BUILD_URL}\nbuild-user-id: ${buildUserId}\nbuild-user: ${buildUser}"
      this.printMessage("comment: ${comment}")
      this.script.steps.sh """
        git config user.email \"${buildUserId}@ic.com\"
  		  git config user.name \"${buildUser}\"
        git commit -m \"${comment}\" """
      String usernameEncoded = encodeHtml('$GITLAB_USER_VALUE')
      String passwordEncoded = encodeHtml('$GITLAB_USER_PASSWORD')
      this.script.steps.wrap([
        $class: 'MaskPasswordsBuildWrapper',
        varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable']]
      ]) {
        this.script.steps.sh """
	        set +x
	        git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} ${releaseBranch}
	        """
      }
    }
    //Update branch name
    branchName = releaseBranch
  }

  protected String getRepositoryPath(hostnameParam,gitRepositoryUrlParam){
    String result = this.script.steps.sh script: """gitRepositoryUrl=${gitRepositoryUrlParam}
    hostname=${hostnameParam}
    hostnameIndexLenght=\$(echo \$(expr length \${hostname}))
    gitRepositoryUrlLenght=\$(echo \$(expr length \${gitRepositoryUrl}))
    gitRepositoryPath=\${gitRepositoryUrl:\${hostnameIndexLenght}:\${gitRepositoryUrlLenght}}
    echo \${gitRepositoryPath}
    """, returnStdout: true
    return result.trim()
  }

  protected boolean existBranch(String branchNameParam){
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String exists = this.script.steps.sh (
        script: """
      exists=false
      branchExistQuery=\$(git branch -r --list origin/$branchNameParam)
      if [ "\${branchExistQuery}" ]
      then
        exists=true
      fi
      echo "\${exists}"
      """, returnStdout: true
      ).trim()
      this.printMessage("exists!: ${exists}")
      return exists=="true"
    }
  }

  protected void deleteNotMergeReleaseBranch(String branchNameParam){
    if(!"${branchNameParam}".startsWith("release/")){
      this.throwException("The release must be done on release")
    }
    //The brans is mergeIntoMaster
    boolean merge = isReleaseBranchMerge(branchNameParam)
    //If yes error throw
    if(merge){
      this.throwException("The release branch is merged previously, the branch ${branchNameParam} can't be deleted")
    }
    //FIX THIS BUG
    unprotectBranch(branchNameParam)
    deleteBranch(branchNameParam)
  }

  protected boolean isReleaseBranchMerge(String branchNameParam){
    String masterBranchName = "master"

    String trace = this.script.steps.sh(script: """
    set +x
    echo "${masterBranchName} > \$(git log -n 1 origin/${masterBranchName} --format=format:%H)"
    """, returnStdout: true).trim()
    this.printMessage("${masterBranchName} commit hash: ${trace}")

    String result = this.script.steps.sh (script: """
    mainCommitHash=\$(git log -n 1 origin/master --format=format:%H)
    commitHashResult=
    for i in \$(git branch -r --merged origin/${masterBranchName} | grep -v -E '(\\*|${masterBranchName}\$)')
    do
      commitHash=\$(git log -n 1 \$i --format=format:%H)
      if [ "\$mainCommitHash" != "\$commitHash" ]; then
        if [ "\$i" == "origin/$branchNameParam" ]; then
          commitHashResult=\$(echo "\$i > \${commitHash}")
        fi
      fi
    done
    echo \${commitHashResult}
    """, returnStdout: true).trim().replaceAll("[\n\r]", "");

    this.printMessage("result: ${result}")
    return result!=""
  }

  protected void unprotectBranch(String branchNameParam){
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String repoGitSlug   = getGitProjectName()
      String projectGitKey = getGitProjectKey()
      def restrictions  = getBitbucketRestrictions(branchNameParam)
      String endpointUrl   = "${bitbucketUrl}/rest/branch-permissions/2.0/projects/${projectGitKey}/repos/${repoGitSlug}/restrictions"
      String basicAuth     = "${script.env.GITLAB_USER_VALUE}:${script.env.GITLAB_USER_PASSWORD}"

      this.printMessage("Unprotecting branch: ${branchNameParam}")

      for (restriction in restrictions){
        this.script.steps.sh("""
            curl -s -u \"${basicAuth}\" -X DELETE \"${endpointUrl}/${restriction}\"
          """)
      }
    }
  }

  public String getGitProjectKey(){
    return this.script.steps.sh(
      script:"echo '${currentGitURL}' | cut -d '/' -f5 | sed 's/.git//g' ",
      returnStdout: true
    ).trim()
  }

  protected getBitbucketRestrictions(String branchName){
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String repoGitSlug = getGitProjectName()
      String projectGitKey = getGitProjectKey()
      String endpointUrl = "${bitbucketUrl}/rest/branch-permissions/2.0/projects/${projectGitKey}/repos/${repoGitSlug}/restrictions?matcherType=BRANCH&matcherId=refs/heads/${branchName}"
      String basicAuth = "${script.env.GITLAB_USER_VALUE}:${script.env.GITLAB_USER_PASSWORD}"

      String jsonResult = this.script.steps.sh(
        script:"""
          set +x
          curl -s -u \"${basicAuth}\" -X GET \"${endpointUrl}\"
        """,
        returnStdout: true).trim()

      JsonSlurper jsonSlurper = new JsonSlurper()
      def jsonResultParsed = jsonSlurper.parseText(jsonResult)

      if(jsonResultParsed["errors"]){
        this.throwException(jsonResultParsed.toString())
      }else if(jsonResultParsed["values"]){
        return jsonResultParsed["values"].collect({it["id"]})
      }

      return []
    }
  }

  protected void deleteBranch(String releaseTagName){
    this.printMessage("Deleting branch: ${releaseTagName}")
    def repositoryPath=getRepositoryPath(gitlabUrl,currentGitURL)
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String usernameEncoded=encodeHtml('$GITLAB_USER_VALUE')
      String passwordEncoded=encodeHtml('$GITLAB_USER_PASSWORD')
      this.script.steps.wrap([
        $class: 'MaskPasswordsBuildWrapper',
        varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable']]
      ]) {
        this.script.steps.sh """ set +x
	        git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} --delete ${releaseTagName}
	        """
      }
    }
  }

  protected String encodeHtml(String value){
    String encodedValue = this.script.steps.sh script: """
    set +x
urlencode() {
    # urlencode <string>
    old_lang=\$LANG
    LANG=C

    old_lc_collate=\$LC_COLLATE
    LC_COLLATE=C
    local length="\${#1}"
    for (( i = 0; i < length; i++ )); do
        local c="\${1:i:1}"
        case \$c in
            [a-zA-Z0-9.~_-]) printf "\$c" ;;
            *) printf '%%%02X' "'\$c" ;;
        esac
    done
    LANG=\$old_lang
    LC_COLLATE=\$old_lc_collate
}
urlencode "${value}"
""", returnStdout: true
    return encodedValue
  }

  protected void createNewReleaseBranch(applicationVersion){
    String newReleaseBranch="release/${applicationVersion}"

    String repositoryPath = getRepositoryPath(gitlabUrl,currentGitURL)
    this.printMessage("Repository path: ${repositoryPath}")

    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String usernameEncoded = encodeHtml('$GITLAB_USER_VALUE')
      String passwordEncoded = encodeHtml('$GITLAB_USER_PASSWORD')
      this.script.steps.sh "git checkout -b ${newReleaseBranch}"
      this.script.steps.wrap([
        $class: 'MaskPasswordsBuildWrapper',
        varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable']]
      ]) {
        this.script.steps.sh """
	        set +x
	        git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} ${newReleaseBranch}
	        """
      }
    }
  }

  protected void protectBranch(String branchNameParam){
    this.printMessage("Protecting branch: ${branchNameParam}")
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      String repoGitSlug   = getGitProjectName()
      String projectGitKey = getGitProjectKey()

      String projectCode  = "${script.env.project}".toUpperCase()
      def exceptGroups = ["inct_gestor_${projectCode}_prod","inct_agileops_prod"]
      def endpointUrl  = "${bitbucketUrl}/rest/branch-permissions/2.0/projects/${projectGitKey}/repos/${repoGitSlug}/restrictions"
      def basicAuth    = "${script.env.GITLAB_USER_VALUE}:${script.env.GITLAB_USER_PASSWORD}"

      String requestPayload = new JsonBuilder([
        [
          type: "read-only",
          matcher:[
            id:"refs/heads/${branchNameParam}",
            type:["id":"BRANCH"],
            active:true
          ],
          groups:["${exceptGroups}"]
        ],
        [
          type: "no-deletes",
          matcher:[
            id:"refs/heads/${branchNameParam}",
            type:["id":"BRANCH"],
            active:true
          ],
          groups:["${exceptGroups}"]
        ]
      ]).toString()
      String result = this.script.steps.sh(
        script:"""
          set +x
          curl -u \"${basicAuth}\" -X POST \"${endpointUrl}\" --header \"Content-Type: application/vnd.atl.bitbucket.bulk+json\" --data \'${requestPayload}\'
        """,
        returnStdout: true).trim()
      this.printMessage("Protect Branch: ${result}")
    }
  }

  protected String updateTagNameWithACR (String tagName=""){
    try{
      def acrKey="${script.params.acr_key}"
      if( acrKey!=null && acrKey!="null" && acrKey!=""){
        this.printMessage("It is a ACR project..!")
        tagName+="-${acrKey}"
        this.printMessage("tagName: ${tagName}")
      }else{
        this.printMessage("It is not a ACR project..!")
      }
    }catch(Exception e){}

    return tagName
  }

  protected void tagBranch(String tagName,String tagStateComment,String environmentParam='') {
    def gitlabCommentPrefix="[ci-${branchName}]"
    def currentDate = buildTimestamp

    if(environmentParam){
      gitlabCommentPrefix="[ci-${branchName}-${environmentParam}]"
    }

    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      this.printMessage("tagName: ${tagName}")
      def tagComment="${gitlabCommentPrefix} ${tagStateComment} ${tagName}\nbranch: ${branchName}\ncommit: ${currentGitCommit}\ndate: ${currentDate}\nbuild-url: ${script.env.BUILD_URL}\nbuild-user-id: ${buildUserId}\nbuild-user: ${buildUser}"
      this.printMessage("tagComment: ${tagComment}")

      def repositoryPath=getRepositoryPath(gitlabUrl,currentGitURL)
      this.printMessage("Repository path: ${repositoryPath}")

      this.script.steps.withCredentials([
        [
          $class: 'UsernamePasswordMultiBinding',
          credentialsId: currentCredentialsId,
          usernameVariable: 'GITLAB_USER_VALUE',
          passwordVariable: 'GITLAB_USER_PASSWORD'
        ]
      ]) {
        String usernameEncoded = encodeHtml('$GITLAB_USER_VALUE')
        String passwordEncoded = encodeHtml('$GITLAB_USER_PASSWORD')

        this.script.steps.sh """
          set +x
          git config user.email \"${buildUserId}@ic.com\"
  		    git config user.name \"${buildUser}\"
          git tag -a ${tagName} -m \"${tagComment}\"
        """
        this.script.steps.wrap([
          $class: 'MaskPasswordsBuildWrapper',
          varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable']]
        ]) {
          this.script.steps.sh """
            set +x
            git config user.email \"${buildUserId}@ic.com\"
  		      git config user.name \"${buildUser}\"
            git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} ${tagName}
	        """
        }
      }
    }
  }

  protected boolean existTag(String tagNameParam){
    def existTag = this.script.steps.sh(script:"""
    existtag="false"
    if git rev-parse -q --verify "refs/tags/${tagNameParam}" >/dev/null; then
			existtag="true"
		fi
    echo \${existtag}
		""", returnStdout: true).trim()
    this.printMessage("Tag exists: ${existTag}")
    return existTag=="true"
  }

  protected void mergeOverwriteBranch(String sourceBranchNameParam, String targetBranchNameParam){
    if(!"${sourceBranchNameParam}".startsWith("RC-")){
      this.throwException("The overwrite must be done from tag release")
    }
    def currentDate = buildTimestamp
    def repositoryPath=getRepositoryPath(gitlabUrl,currentGitURL)
    def gitlabCommentPrefix="[ci-${targetBranchNameParam}]"
    def stateComment="Merge-overwrite-release-tag-into-master"
    def comment="${gitlabCommentPrefix} ${stateComment} \nbranch-source: ${sourceBranchNameParam}\ncommit-source: ${currentGitCommit}\ndate: ${currentDate}\nbuild-url: ${script.env.BUILD_URL}\nbuild-user-id: ${buildUserId}\nbuild-user: ${buildUser}"
    // update all our origin/* remote-tracking branches
    //fetch()
    this.script.steps.sh """
      git config user.email \"${buildUserId}@ic.com\"
  		git config user.name \"${buildUser}\"
    """

    this.script.steps.sh """
    git branch --list
    git checkout -b ${targetBranchNameParam} origin/${targetBranchNameParam}
    git checkout -b temp-${sourceBranchNameParam} refs/tags/${sourceBranchNameParam}

    git merge -v -s ours ${targetBranchNameParam} -m \"${comment}\"
    git status -v
    git status -v -sb

    git checkout ${targetBranchNameParam}
    git merge -v --no-ff -X theirs temp-${sourceBranchNameParam} -m \"${comment}\"
    git status -v
    git status -v -sb
    """
    this.script.steps.withCredentials([
      [
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: currentCredentialsId,
        usernameVariable: 'GITLAB_USER_VALUE',
        passwordVariable: 'GITLAB_USER_PASSWORD'
      ]
    ]) {
      def usernameEncoded=encodeHtml('$GITLAB_USER_VALUE')
      def passwordEncoded=encodeHtml('$GITLAB_USER_PASSWORD')
      this.script.steps.wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable'],]]) {
        this.script.steps.sh """set +x
	        git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} ${targetBranchNameParam}
	        """
      }
    }
  }

  protected void deleteTagBranch(String tagName) {
    this.script.steps.withCredentials([[
                                         $class: 'UsernamePasswordMultiBinding',
                                         credentialsId: this.currentCredentialsId,
                                         usernameVariable: 'GITLAB_USER_VALUE',
                                         passwordVariable: 'GITLAB_USER_PASSWORD'
                                       ]]) {
      this.printMessage("tagName: ${tagName}")

      def repositoryPath = this.getRepositoryPath(gitlabUrl,currentGitURL)
      this.printMessage("Repository path: ${repositoryPath}")
      this.script.steps.withCredentials([[
                                           $class: 'UsernamePasswordMultiBinding',
                                           credentialsId: this.currentCredentialsId,
                                           usernameVariable: 'GITLAB_USER_VALUE',
                                           passwordVariable: 'GITLAB_USER_PASSWORD'
                                         ]]) {
        def usernameEncoded = this.encodeHtml('$GITLAB_USER_VALUE')
        def passwordEncoded = this.encodeHtml('$GITLAB_USER_PASSWORD')
        this.script.steps.wrap([
          $class: 'MaskPasswordsBuildWrapper',
          varPasswordPairs: [[password: passwordEncoded, var: 'passwordEncodedVariable']]
        ]) {
          this.script.steps.sh """
          set +x
          git tag -d ${tagName}
          git fetch ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} --tags
          git push ${script.env.GITLAB_PROTOCOL}://${usernameEncoded}:${passwordEncoded}@${script.env.GITLAB_HOST}:${script.env.GITLAB_PORT}${script.env.SCM_REPOSITORY_PREFIX}${repositoryPath} :${tagName}
          git tag -d ${tagName}
          """
        }
      }
    }
  }

  protected void promoteRepository(String applicationName, String releaseTagName, String repoSuffix=""){
    def environment = "CERT"
    def repositoryURL = "${artefactoryUrl}"
    def currentDate = buildTimestamp

    if(!"${branchName}".startsWith("tags/RC-")){
      this.throwException("The release must be done on release")
    }
    def buildNumber = "${releaseTagName}"
    def buildName = "${this.script.env.project}".toLowerCase()+"-${applicationName}"
    def sourceRepo = "${this.script.env.project}${repoSuffix}-${environment}"
    def targetRepo = "${this.script.env.project}${repoSuffix}.Release"
    def ciName = "jks-${buildUserId}"
    def actionURL="${repositoryURL}/api/build/promote/${buildName}/${buildNumber}"
    def timestamp = this.script.steps.sh(script: "echo \$(date +\"%Y-%m-%dT%H:%M:%S.%3N%z\")", returnStdout: true).trim()
    def gitlabCommentPrefix="[ci-${releaseTagName}]"
    def stateComment="Promote-release"
    def comment="${gitlabCommentPrefix} ${stateComment} \nbranch: ${branchName}\ncommit: ${currentGitCommit}\ndate: ${currentDate}\nbuild-url: ${script.env.BUILD_URL}\nbuild-user-id: ${buildUserId}\nbuild-user: ${buildUser}"
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

    this.docker.withServer("${this.script.env.DOCKER_HOST}"){
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
    }
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

  /*
  * Deploy application to Play Store
  */
  public void deployApplicationToPlayStore(String projectName, String appName, String packageName, String apkAbsolutePath, String track){
    def clientEmailCredentialsFileIdName       = "${projectName}-${appName}-client-email"
    def googleServiceJsonCredentialsFileIdName = "${projectName}-${appName}-google-oauth2-service-json"
    def currentDate                            = "${buildTimestamp}"

    printMessage("Deploying to Google Play Store")
    printMessage("NOTE: - The credentials should be populate on project folder credentials")
    printMessage("Google Service Account - Client Email (Credentials String): ${clientEmailCredentialsFileIdName}")
    printMessage("Google Service Account - Service Account Json (Credentials File): ${googleServiceJsonCredentialsFileIdName}")

    script.steps.writeFile(
      file:"${script.env.WORKSPACE}/play-store-deploy-apk.py",
      text: getPlayStorePublishPythonScript()
    )

    script.steps.withCredentials([
      [$class: "StringBinding", credentialsId: "${clientEmailCredentialsFileIdName}", variable: "clientEmailValue"],
      [$class: "FileBinding", credentialsId: "${googleServiceJsonCredentialsFileIdName}", variable: "storeFilePath"]
    ]) {

      script.steps.sh "cp ${script.env.storeFilePath} ${script.env.WORKSPACE}/google-oauth2-service-${currentDate}.json"

      docker.withServer("${script.env.DOCKER_HOST}"){
        docker.withRegistry("http://${script.env.DOCKER_REGISTRY_URL}") {
          def dockerParameters = "--network=host"
          def dockerVolumen    = "-v ${script.env.WORKSPACE}:${script.env.WORKSPACE} -w ${script.env.WORKSPACE}"
          def dockerCmd        = "docker run --rm ${dockerVolumen} ${dockerParameters} ${dockerPython27GoogleApiEnviroment}"

          script.steps.echo "dockerVolumen: ${dockerVolumen}"
          script.steps.sh "${dockerCmd} bash -c \"python ${script.env.WORKSPACE}/play-store-deploy-apk.py ${packageName} ${apkAbsolutePath} ${track} ${script.env.clientEmailValue} ${script.env.WORKSPACE}/google-oauth2-service-${currentDate}.json\""
        }
      }

      try{
        script.steps.sh """
          set +x
          rm -f ${script.env.WORKSPACE}/google-oauth2-service-${currentDate}.json
        """
      }catch(Exception e) {
        script.steps.sh "echo \"${e}\""
      }

    }
  }

  protected def getPlayStorePublishPythonScript(){
    def result = """#!/usr/bin/python
import argparse
import mimetypes

from apiclient.discovery import build
import httplib2
from oauth2client import client
from oauth2client.service_account import ServiceAccountCredentials

# Declare command-line flags.
argparser = argparse.ArgumentParser(add_help=False)
argparser.add_argument('package_name',
                       help='The package name. Example: com.android.sample')
argparser.add_argument('apk_file',
                       nargs='?',
                       default='test.apk',
                       help='The path to the APK file to upload.')
argparser.add_argument('track',
                       nargs='?',
                       default='alpha',
                       help='Can be \\'alpha\\', \\'beta\\', \\'production\\' or \\'rollout\\'')
argparser.add_argument('service_account_email',
                       help='The service account. Example: ENTER_YOUR_SERVICE_ACCOUNT_EMAIL_HERE@developer.gserviceaccount.com')
argparser.add_argument('keyfilename',
                       help='The key file name. Example: Filename of the key')

def main():
  mimetypes.add_type("application/octet-stream", ".apk")
  mimetypes.add_type("application/octet-stream", ".aab")
  # Load the key in PKCS 12 format that you downloaded from the Google APIs
  # Console when you created your Service account.

  # Process flags and read their values.
  flags = argparser.parse_args()
  package_name = flags.package_name
  apk_file = flags.apk_file
  track = flags.track
  extensionPosition=apk_file.rindex(".")
  extension=apk_file[extensionPosition+1:]
  print 'extension: %s' % extension
  service_account_email = flags.service_account_email
  keyfilename = flags.keyfilename
  credentials = ServiceAccountCredentials.from_json_keyfile_name(keyfilename, scopes=[
      'https://www.googleapis.com/auth/androidpublisher',
      ])

  credentials = credentials.create_delegated(service_account_email)
  http = httplib2.Http()
  http = credentials.authorize(http)
  service = build('androidpublisher', 'v3', http=http)

  try:
    edit_request = service.edits().insert(body={}, packageName=package_name)
    result = edit_request.execute()
    edit_id = result['id']

  #  apks_result = service.edits().apks().list(
  #      editId=edit_id, packageName=package_name).execute()

  #  for apk in apks_result['apks']:
  #    print 'versionCode: %s, binary.sha1: %s' % (
  #        apk['versionCode'], apk['binary']['sha1'])

    if(extension=='apk'):
      print 'apk deploy'
      apk_response = service.edits().apks().upload(
      editId=edit_id,
      packageName=package_name,
      media_body=apk_file).execute()
    elif(extension=='aab'):
      print 'bundle deploy'
      apk_response = service.edits().bundles().upload(
      editId=edit_id,
      packageName=package_name,
      media_body=apk_file).execute()
    else:
      print 'not supported'

    print 'apk_response %s' % apk_response

    print 'Version code %d has been uploaded' % apk_response['versionCode']

    versionCode = str(apk_response['versionCode'])
    track_response = service.edits().tracks().update(
        editId=edit_id,
        track=track,
        packageName=package_name,
        body={u'releases': [{
            u'name': u'Release ' + versionCode,
            u'versionCodes': [versionCode],
            u'status': u'completed',
        }]}).execute()

    print 'Track %s is set with releases: %s' % (
        track_response['track'], str(track_response['releases']))

    commit_request = service.edits().commit(
        editId=edit_id, packageName=package_name).execute()

    print 'Edit "%s" has been committed' % (commit_request['id'])

  except client.AccessTokenRefreshError:
    print ('The credentials have been revoked or expired, please re-run the '
           'application to re-authorize')

if __name__ == '__main__':
  main()
"""
    result=result.stripIndent()
    return result
  }

  /*
  * Deploy application to AppCenter Store
  */
  public void deployAppToAppCenter(String ownerName, String appName, String applicationRelativePath, String distributeGroup='Collaborators'){
    def buildTimestamp=buildTimestamp
    def apiTokenStringCredentialsId="${script.env.project}".toLowerCase()+"-appcenter-apitoken-credentialid"
    printMessage("Deploying to AppCenter")
    printMessage("NOTE: - The credentials should be populate on project folder credentials")
    printMessage("API Token (Credentials String): ${apiTokenStringCredentialsId}")

    printMessage("buildTimestamp: ${buildTimestamp}")
    printMessage("appName: ${appName}")
    printMessage("ownerName: ${ownerName}")
    script.steps.withCredentials([[$class: "StringBinding", credentialsId: "${apiTokenStringCredentialsId}", variable: "apiToken"]]) {
      script.steps.sh (
        script: """
            curl -D ${script.env.WORKSPACE}/appcenter-response-headers-${buildTimestamp} \\
                 -o ${script.env.WORKSPACE}/appcenter-response-result-${buildTimestamp} -X POST \\
                --header "Content-Type: application/json" \\
                --header "Accept: application/json" \\
                --header "X-API-Token: ${script.env.apiToken}" \\
                "https://api.appcenter.ms/v0.1/apps/${ownerName}/${appName}/release_uploads"
            """,
        returnStdout: true)

      script.steps.sh "cat ${script.env.WORKSPACE}/appcenter-response-headers-${buildTimestamp}"
      script.steps.sh "cat ${script.env.WORKSPACE}/appcenter-response-result-${buildTimestamp}"

      def errorCode = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-response-headers-${buildTimestamp} | grep 'HTTP/1.1' | tail -n1 | cut -d' ' -f2", returnStdout: true).trim()
      printMessage("Error code: ${errorCode}")
      //Artefact has promoted or moved
      if(!errorCode.startsWith("20")){
        throwException("Error create upload resource to the App Center")
      }

      def uploadUrl = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-response-result-${buildTimestamp} | jq --raw-output '.upload_url'", returnStdout: true).trim()
      def uploadId = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-response-result-${buildTimestamp} | jq --raw-output '.upload_id'", returnStdout: true).trim()
      printMessage("uploadUrl: ${uploadUrl}")
      printMessage("uploadId: ${uploadId}")
      script.steps.sh (
        script: """
            curl -D ${script.env.WORKSPACE}/appcenter-upload-response-headers-${buildTimestamp} \\
            -F "ipa=@${applicationRelativePath}" ${uploadUrl}
            """,
        returnStdout: true)

      errorCode = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-upload-response-headers-${buildTimestamp} | grep 'HTTP/1.1' | tail -n1 | cut -d' ' -f2", returnStdout: true).trim()
      printMessage("Error code: ${errorCode}")
      //Artefact has promoted or moved
      if(!errorCode.startsWith("20")){
        throwException("Error uploading app to the App Center")
      }

      script.steps.sh(
        script: """
            curl -D ${script.env.WORKSPACE}/appcenter-release-response-headers-${buildTimestamp} \\
                -o ${script.env.WORKSPACE}/appcenter-release-response-result-${buildTimestamp} \\
                -X PATCH \\
                --header "Content-Type: application/json" \\
                --header "Accept: application/json" \\
                --header "X-API-Token: ${script.env.apiToken}" \\
                -d '{ "status": "committed"  }' \\
                \"https://api.appcenter.ms/v0.1/apps/${ownerName}/${appName}/release_uploads/${uploadId}\"
            """,
        returnStdout: true)

      errorCode = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-release-response-headers-${buildTimestamp} | grep 'HTTP/1.1' | tail -n1 | cut -d' ' -f2", returnStdout: true).trim()
      printMessage("Error code: ${errorCode}")
      //Artefact has promoted or moved
      if(!errorCode.startsWith("20")){
        throwException("Error uploading app to the App Center")
      }

      def releaseUrl = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-release-response-result-${buildTimestamp} | jq --raw-output '.release_url'", returnStdout: true).trim()
      printMessage("releaseUrl: ${releaseUrl}")

      script.steps.sh(
        script: """
            curl -D "${script.env.WORKSPACE}/appcenter-distribution-response-headers-${buildTimestamp}" \\
                -o "${script.env.WORKSPACE}/appcenter-distribution-response-result-${buildTimestamp}" \\
                -X PATCH \\
                --header "Content-Type: application/json" \\
                --header "Accept: application/json" \\
                --header "X-API-Token: ${script.env.apiToken}" \\
                -d '{ "destination_name": "${distributeGroup}", "release_notes": "User Id: ${buildUserId}, User Name: ${buildUser}, Release date: ${buildTimestamp}, Commit:${currentGitCommit}, Build Server Url:${script.env.BUILD_URL}, Repository Url:${currentGitURL}" }' \\
                \"https://api.appcenter.ms/${releaseUrl}\"
            """,
        returnStdout: true)

      errorCode = script.steps.sh (script:  "cat ${script.env.WORKSPACE}/appcenter-distribution-response-headers-${buildTimestamp} | grep 'HTTP/1.1' | tail -n1 | cut -d' ' -f2", returnStdout: true).trim()
      printMessage("Error code: ${errorCode}")
    }
  }

  protected void setXrayEnabled(boolean xrayActivatedParam) {
    this.xrayActivated = xrayActivatedParam
  }

  def executeXraySCA(String buildName, String buildNumber) {
    if (!xrayActivated) {
      printMessage("********************* XRAY IS DISABLED!!!!! *********************")
      return
    }

    printMessage("buildName=${buildName}")
    printMessage("buildNumber=${buildNumber}")

    if(!buildName || !buildNumber) {
      this.script.steps.error("Es necesario pasar los parametros buildName, buildNumber")
    }

    def xrayArtifactoryId = "${script.env.XRAY_ARTIFACTORY_ID}"
    def xrayScanEndpoint = "${script.env.XRAY_SERVER_URL}"+'/api/v1/scanBuild'

    def projectLowerCase         = "${script.env.project}".toLowerCase()
    def resultsFile              = "xrayScanResult-${buildTimestamp}.json";
    def artefactoryCredentialsId = "xray-admin-user"

    def jsonPayload = """{"artifactoryId": "${xrayArtifactoryId}","buildName": "${buildName}","buildNumber":"${buildNumber}"}"""

    script.steps.withCredentials([
      [ $class: 'UsernamePasswordMultiBinding',
        credentialsId   : artefactoryCredentialsId,
        usernameVariable: 'ARTIFACT_USER_NAME',
        passwordVariable: 'ARTIFACT_USER_PASSWORD']
    ]){
      script.env.ARTIFACT_USER_PASSWORD = script.env.ARTIFACT_USER_PASSWORD.replaceAll( /([^a-zA-Z0-9])/, '\\\\$1' )
      this.script.steps.sh "curl -L -X POST -u ${script.env.ARTIFACT_USER_NAME}:${script.env.ARTIFACT_USER_PASSWORD} -H \"Content-Type: application/json\" -d \'${jsonPayload}\' ${xrayScanEndpoint} -o ${resultsFile}"

      def failBuild = this.script.steps.sh(script:"""
        jq '.summary.fail_build' ${resultsFile}
      """,returnStdout:true).trim().toBoolean();

      def message = this.script.steps.sh(script:"""
        jq '.summary.message' ${resultsFile}
      """,returnStdout:true).trim();

      def resultsUrl = this.script.steps.sh(script:"""
        jq '.summary.more_details_url' ${resultsFile}
      """,returnStdout:true).trim();

      def totalAlerts = this.script.steps.sh(script:"""
        jq '.summary.total_alerts' ${resultsFile}
      """,returnStdout:true).trim().toInteger();

      if(failBuild) {
        if (totalAlerts == 0) {
          this.script.steps.error """
            * [BCP-DevSecOps] Analisis SCA: FAIL
            * Message: ${message}
          """
        } else {
          this.script.steps.error """
            *****************************************
            * [BCP-DevSecOps] Analisis SCA: FAIL
            * Message: Para continuar con el flujo de ejecucion del Pipeline, tu aplicacion debe estar libre de vulnerabilidades con nivel de criticidad HIGH. ${message}
            * Para mayor informacion consulte el reporte de seguridad: ${resultsUrl}
            *****************************************
          """
        }
      } else {
        this.script.steps.echo """
          * [BCP-DevSecOps] Analisis SCA: SUCCESS
          * Para mayor informacion consulte el reporte de seguridad: ${resultsUrl}
        """
      }
    }
  }

  protected void withAzureVaultCredentials(ArrayList credentialList, Closure body){
    def buildTimestamp  = buildTimestamp
    def httpProxyHost = "${script.env.DOCKER_HTTP_PROXY_HOST}"
    def httpProxyPort = "${script.env.DOCKER_HTTP_PROXY_PORT}"
    String  projectName     = "${script.env.project}".toLowerCase()
    HashMap tempCredentials = new HashMap()

    String azureVaultName        = "${projectName}-azure-vault-name"
    String azureServicePrincipal = "${projectName}-azure-sp-id"
    String azureCertificate      = "${projectName}-azure-sp-certificate"
    String azureTenantId         = "${projectName}-azure-sp-tenant"

    this.printMessage("NOTE: - The credentials should be populate on project folder credentials")
    this.printMessage("azureVaultName(Credentials String): ${azureVaultName}")
    this.printMessage("azureServicePrincipal(Credentials String): ${azureServicePrincipal}")
    this.printMessage("azureCertificate(Credentials File): ${azureCertificate}")
    this.printMessage("azureTenantId(Credentials String): ${azureTenantId}")

    def tempWSFolder   = "${script.env.WORKSPACE}@tmp-${buildTimestamp}"
    def tempFolder     = "${tempWSFolder}/.azure-${buildTimestamp}"
    def secretCertFile = "${tempFolder}/.tmp/.azure-cert-${buildTimestamp}"

    try{
      this.script.steps.withCredentials([
        [$class: "StringBinding", credentialsId: "${azureVaultName}", variable: "azureVaultNameVariable"],
        [$class: "StringBinding", credentialsId: "${azureServicePrincipal}", variable: "azureServicePrincipalVariable"],
        [$class: "FileBinding", credentialsId: "${azureCertificate}", variable: "azureCertPathVariable"],
        [$class: "StringBinding", credentialsId: "${azureTenantId}", variable: "azureTenantIdVariable"]
      ]){

        int keyCount=0
        this.script.steps.sh """
          set +x
          mkdir -p ${tempFolder}/.tmp
          cp -f ${script.env.azureCertPathVariable} ${secretCertFile}
        """

        //Check credentials and set credentials, if not exist BOOM!
        for(Map credentialsArray: credentialList){
          String dockerProxyParam=""

          if(httpProxyHost && httpProxyPort){
            dockerProxyParam="export https_proxy=http://${httpProxyHost}:${httpProxyPort} &&"
          }

          def azureCredentialType         = credentialsArray.get('azureCredentialType')
          def azureCredentialId           = credentialsArray.get('azureCredentialId')
          def azureCredentialVariableName = credentialsArray.get('azureCredentialVariable')
          this.printMessage("Azure Credential to use: ${azureCredentialId}")

          docker.withServer("${script.env.DOCKER_HOST}"){
            docker.withRegistry("http://${script.env.DOCKER_REGISTRY_URL}") {
              def dockerParameters="--network=host --cpus 2 -v ${script.env.WORKSPACE}:/opt/jenkins/data/temp -v ${tempWSFolder}:/opt/jenkins/data/.temp"
              //TOFIX: Bug on Azure implementation, the .azure folder can not be set with other user different to root
              //-v ${tempWSFolder}/.azure-${buildTimestamp}/.azure:/opt/jenkins/data/.azure"
              def certPath="/opt/jenkins/data/.temp/.azure-${buildTimestamp}/.tmp/.azure-cert-${buildTimestamp}"
              def dockerCmd="docker run --rm ${dockerParameters} ${dockerAzureClientEnviroment} "

              def azCmd="${dockerProxyParam}az login --service-principal --username ${script.env.azureServicePrincipalVariable} --password \"${certPath}\" --tenant ${script.env.azureTenantIdVariable} > /opt/jenkins/data/.temp/log-${buildTimestamp}${keyCount}.log && az keyvault secret show --name ${azureCredentialId} --vault-name ${script.env.azureVaultNameVariable} --query value "

              def azureCredentialValue
              this.script.steps.wrap([
                $class: 'MaskPasswordsBuildWrapper',
                varPasswordPairs: [[password: certPath, var: 'certPathVariable']]
              ]) {
                azureCredentialValue = this.script.steps.sh (
                  script: "${dockerCmd} bash -c \"${azCmd}\"",
                  returnStdout: true
                ).trim()
              }

              this.script.steps.sh """
                set +x
                cat ${tempWSFolder}/log-${buildTimestamp}${keyCount}.log
              """

              if(azureCredentialValue==""){
                this.throwException("The secret "+azureCredentialId+" is not founded or the value is empty?")
              }

              if(azureCredentialType=="file"){
                this.script.steps.writeFile(
                  file:"${tempFolder}/secretfileBase64",
                  text: azureCredentialValue
                )

                this.script.steps.sh """
                jq --raw-output '' ${tempFolder}/secretfileBase64 > ${tempFolder}/secretfileBase64Mod1
                sed 's/\\\\n/\\n/g' ${tempFolder}/secretfileBase64Mod1 > ${tempFolder}/secretfileBase64Mod2
                base64 -d ${tempFolder}/secretfileBase64Mod2 > ${tempFolder}/${azureCredentialVariableName}
                """
                tempCredentials.put("${azureCredentialVariableName}","${tempFolder}/${azureCredentialVariableName}")
              }else{
                this.script.steps.writeFile(
                  file:"${script.env.WORKSPACE}/secretfile",
                  text: azureCredentialValue
                )

                azureCredentialValue = this.script.steps.sh (
                  script: """
                  set +x
                  cat ${script.env.WORKSPACE}/secretfile | jq --raw-output ''""",
                  returnStdout: true
                ).trim()
                tempCredentials.put("${azureCredentialVariableName}","${azureCredentialValue}")
              }
            }
          }
        }
      }

      ArrayList environmentContextList = new ArrayList()
      ArrayList passwordPairList      = new ArrayList()

      tempCredentials.each {
        key, value ->
          environmentContextList.add("$key=$value");
          HashMap passwordPair=new HashMap()
          passwordPair.put("password","$value")
          passwordPair.put("var","${key}Var")
          passwordPairList.add(passwordPair)
      }

      //Execute commands
      this.script.steps.wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: passwordPairList]) {
        this.script.steps.withEnv(environmentContextList){
          body.call()
        }
      }

    }finally{
      this.script.steps.sh """
        set +x
        if [ -d \"${tempWSFolder}\" ]; then
          rm -rf \"${tempWSFolder}\"
        fi
      """
    }
  }

  protected void generateReportFortify() {
    this.script.steps.sh """
      set -x
      
      #Get High issues
      cat MyOWASP_Top10_Report.html | grep -A 4 -m 10 ">High<" | grep '<div class.*</div>' | sed 's%</div>%%g' | sed 's/<div class="style_92" style=" overflow:hidden;">//g' | awk '{\$1=\$1;print}' > .tmp
      total=0
      while IFS=' ' read -r valor
      do
        echo \$valor
        total=\$((\$total + \$valor))
      done < .tmp
      echo \$total > CANTIDAD_HIGH
      #remove temporal file
      rm -f .tmp
      
      #Get Critical issues
      cat MyOWASP_Top10_Report.html | grep -A 4 -m 10 ">Critical<" | grep '<div class.*</div>' | sed 's%</div>%%g' | sed 's/<div class="style_92" style=" overflow:hidden;">//g' | awk '{\$1=\$1;print}' > .tmp
      total=0
      while IFS=' ' read -r valor
      do
        echo \$valor
        total=\$((\$total + \$valor))
      done < .tmp
      echo \$total > CANTIDAD_CRITICAL
      #remove temporal file
      rm -f .tmp
    """
    def fortifyVersion = getGitProjectName()

    this.script.steps.sh """
      set -x
          if [ -f .version.txt ]; then
             rm .version.txt
          fi
          if [ -f .matchproject ]; then
             rm .matchproject
          fi

      cat .listProject.txt | while IFS=' ' read -r p1
         do
            echo \$p1 | awk '{print \$3}' > .repositories
            repos=`cat .repositories`
            echo \$repos
            if [ "${fortifyVersion}" == "\$repos" ]; then
              echo \$p1 >> .matchproject
            fi
         done
      cat .matchproject | grep ${script.env.project} | grep ${fortifyVersion} | awk '{print \$1}' > .version.txt
    """

    def idVerFortify      = (this.script.steps.readFile('.version.txt').trim()).toInteger()
    def CANTIDAD_HIGH     = (this.script.steps.readFile('CANTIDAD_HIGH').trim()).toInteger()
    def CANTIDAD_CRITICAL = (this.script.steps.readFile('CANTIDAD_CRITICAL').trim()).toInteger()

    this.script.steps.echo """
    ******* Analisis SAST Fortify *******
    * Vulnerabilidades HIGH    : ${CANTIDAD_HIGH}
    * Vulnerabildiades CRITICAL: ${CANTIDAD_CRITICAL}
    * URL                      : ${fortifyUrl}/html/ssc/version/${idVerFortify}/fix
    """

    this.script.steps.archiveArtifacts(artifacts: 'MyOWASP_Top10_Report.html')

    //Validar si contiene vulnerabilidades bloqueantes
    if (CANTIDAD_HIGH==0 && CANTIDAD_CRITICAL==0){
      this.script.steps.echo "No posee vulnerabiblidades de criticidad Alta o Critica del OWASP TOP 10 - 2017"
    } else {
      this.script.steps.error 'Security validation failed when running tests on Fortify. To pass the validation your application must be free of Criticality Vulnerabilities High and/or Critical'
    }
  }
}
