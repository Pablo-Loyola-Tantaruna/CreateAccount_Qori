@Library('jenkins-sharedlib@master')
import sharedlib.JenkinsfileUtil

def utils = new JenkinsfileUtil(steps, this)
// Project settings
def project = "QTECH_UX"
// Namespace settings
def namespace = "QTECH"
// Mail configuration
// If recipient is null the mail will be sent to the person who start the job
// The mails should be separated by comma (,)
def recipients = "sergio_23_loyola@hotmail.com"
def deploymentEnvironment = "dev"
// ocp | aks
def hosted = "aks"
// ocp311 | ocpa4
def ocpVersion = "ocpa4"
//MAVEN_339_JDK_17_OPENJ9 | MAVEN_339_JAVA8
def javaVersion = "MAVEN_339_JDK_17_OPENJ9"
//OPTIONAL: Subscription Id for AKS Cluster
def aksSubscriptionId = "e4b3b3b3-3b3b-3b3b-3b3b-3b3b3b3b3b3b"
//for AKS Resource Group, for anythind else: def aksRG = ""
def aksRG = "RSGREU2QTECHBD01"
//for AKS Cluster Name, for anything else: def aksCluster = ""
def aksCluster = "AKSREU2QTECHBD01"
//Ingress information for AKS
def ingressFQDN = "azingressqtechdev.perzet.com"
def ingressCertificateSecret = "azingressqtechdev-cert"
//registry for AKS
def registry = "azingressqtechdev.azurecr.io"
try {
    node {
        stage('Preparation') {
            println "Start pipeline for project: ${project} : "
            env.TIME_DELTA_INIT = steps.sh(script: 'date +%s', returnStdout: true).toString.trim()
            cleanWs()
            utils.notifyByMail("Start pipeline for project: ${project} : ", recipients)
            checkout scm
            utils.prepare()
            //Setup parameters
            env.project = "${project}"
            //utils.setHashicorpVaultEnabled(false)
            //utils.setHashicorpVaultEnviorement("${deploymentEnvironment}")
            //utils.setHashicorpVaultInstance(false)
            //utils.setHashicorpVaultNamespace("ZETA_FUXT")
            utils.getDelta("prepare")
        }
        //values for k8s annotations
        def gitrepo = "${utils.currentGitURL}"
        def repobranch = "${utils.branchName}"
        def gitcommit = "${utils.currentGitCommit}"

        stage('Build & U.Test') {
            utils.setDeltaInit()
            utils.setMavenJavaVersion(javaVersion)
            utils.buildMaven("-U", false)
            utils.getDelta("build")
        }
        stage('QA Analisys') {
            utils.setDeltaInit()
            utils.executeSonar()
            utils.getDelta("qa")
        }
        stage('Upload Artifact') {
            utils.setDeltaInit()
            utils.deployArtifactoryMaven()
            utils.getDelta("upload_artifactory")
        }
        stage('Xray Scan') {
            utils.executeXrayScan()
        }
        stage('Save Results') {
            utils.setDeltaInit()
            utils.saveResultMaven('jar')
            utils.getDelta("save_results")
        }
        milestone(label: 'Prepare for release to ' + deploymentEnvironment)

        stage('Application Delivery') {
            utils.setDeltaInit()
            utils.downloadSingleFilefromRepo("QTECH", "maintenance-tasks", "develop", "github-token-${deploymentEnvironment}",
            "jenkins/internal-compact/eq_gt_1.9.0/hashicorpvault/internal-delivery-single-pipeline.groovy",
            "${WORKSPACE}/devops/jenkins/internal-delivery-pipeline.groovy")
            def deliverybranchname = "1.9.1"
            def mapVars = [:]
            mapVars << ["utils": utils]
            mapVars << ["project": project]
            mapVars << ["namespace": namespace]
            mapVars << ["deploymentEnvironment": deploymentEnvironment]
            mapVars << ["hosted": hosted]
            mapVars << ["ocpVersion": ocpVersion]
            mapVars << ["javaVersion": javaVersion]
            mapVars << ["aksSubscriptionId": aksSubscriptionId]
            mapVars << ["aksRG": aksRG]
            mapVars << ["aksCluster": aksCluster]
            mapVars << ["ingressFQDN": ingressFQDN]
            mapVars << ["ingressCertificateSecret": ingressCertificateSecret]
            mapVars << ["registry": registry]
            mapVars << ["gitrepo": gitrepo]
            mapVars << ["repobranch": repobranch]
            mapVars << ["gitcommit": gitcommit]
            mapVars << ["deliverybranchname": deliverybranchname]
            mapVars << ["deliveryref": "tag"]
            load("devops/jenkins/internal-delivery-pipeline.groovy").deliveryApplication(mapVars)
            utils.getDelta("application_delivery")
        }
        stage('Application Deployment') {
            utils.setDeltaInit()
            utils.downloadSingleFilefromRepo("QTECH", "maintenance-tasks", "develop", "github-token-${deploymentEnvironment}",
            "jenkins/internal-compact/eq_gt_1.9.0/hashicorpvault/internal-deployment-single-pipeline.groovy",
            "${WORKSPACE}/devops/jenkins/internal-deployment-pipeline.groovy")
            def deploymentbranchname = "1.9.1"
            def mapVars = [:]
            mapVars << ["utils": utils]
            mapVars << ["project": project]
            mapVars << ["namespace": namespace]
            mapVars << ["deploymentEnvironment": deploymentEnvironment]
            mapVars << ["hosted": hosted]
            mapVars << ["ocpVersion": ocpVersion]
            mapVars << ["javaVersion": javaVersion]
            mapVars << ["aksSubscriptionId": aksSubscriptionId]
            mapVars << ["aksRG": aksRG]
            mapVars << ["aksCluster": aksCluster]
            mapVars << ["ingressFQDN": ingressFQDN]
            mapVars << ["ingressCertificateSecret": ingressCertificateSecret]
            mapVars << ["registry": registry]
            mapVars << ["gitrepo": gitrepo]
            mapVars << ["repobranch": repobranch]
            mapVars << ["gitcommit": gitcommit]
            mapVars << ["deploymentbranchname": deploymentbranchname]
            mapVars << ["deploymentref": "tag"]
            load("devops/jenkins/internal-deployment-pipeline.groovy").deploymentApplication(mapVars)
            utils.getDelta("application_deployment")
        }
        stage('Post Execution') {
            utils.printLogstashInfo("End pipeline for project: ${project} : ", "${deploymentEnvironment}", "${hosted}", "JOB-TYPE: JAVA-MS")
            utils.executePostExecutionTasks()
            utils.notifyByMail("SUCCESS - End pipeline for project: ${project} : ", recipients)
        }
    }
} catch (Exception ex) {
    node {
        utils.printLogstashInfo("Error in pipeline for project: ${project} : ", "${deploymentEnvironment}", "${hosted}", "JOB-TYPE: JAVA-MS")
        utils.executeOnErrorExecutionTasks()
        utils.notifyByMail("Error in pipeline for project: ${project} : ", recipients)
        throw ex
    }
}