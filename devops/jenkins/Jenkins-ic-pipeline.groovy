@Library('jenkins-sharedlib@master')
import sharedlib.JenkinsfileUtil

def utils = new JenkinsfileUtil(steps, this)
// Project settings
def project = "QTECH_UX"
// Mail configuration
// If recipient is null the mail will be sent to the person who start the job
// The mails should be separated by comma (,)
def recipients = ""
def deploymentEnvironment = "dev"
try {
    node {
        stage('Preparation') {
            utils.notifyByMail("Start pipeline for project: ${project} : ", recipients)
            checkout scm
            utils.prepare()
            //Setup parameters
            env.project = "${project}"
        }
        stage('Build & U.Test') {
            utils.buildMaven("-U", false)
        }
        stage('QA Analisys') {
            utils.executeSonar()
        }
        stage('Upload Artifact') {
            utils.deployArtifactoryMaven()
        }
        stage('Save Results') {
            utils.saveResultMaven('jar')
        }
        stage('Post Execution') {
            utils.executePostExecutionTasks()
            utils.notifyByMail("End pipeline for project: ${project} : ", recipients)
        }
    }
} catch (Exception ex) {
    node {
        utils.executeOnErrorExecutionTasks()
        utils.notifyByMail("Error in pipeline for project: ${project} : ", recipients)
        throw ex
    }
}