@Library('jenkins-shared-library') _

def pod = libraryResource 'io/milvus/pod/tekton.yaml'
def output = [:]
// def jobName = ''

pipeline {
    options {
        skipDefaultCheckout true
    }
    agent {
        kubernetes {
            cloud '4am'
            yaml pod
        }
    }
    stages {
        stage('build-and-push') {
            steps {
                container('kubectl') {
                    script {
                        // println "$env.CHANGE_TARGET"
                        // println  "$env.CHANGE_ID"
                        // println  "$env.CHANGE_TARGET"
                        isPr = env.CHANGE_ID != null
                        gitMode = isPr ? 'merge' : 'fetch'
                        gitBaseRef = isPr ? "$env.CHANGE_TARGET" : "$env.BRANCH_NAME"

                        jobName = tekton.buildConanfiles arch: 'amd64',
                                              isPr: isPr,
                                              gitMode: gitMode ,
                                              gitBaseRef: gitBaseRef,
                                              pullRequestNumber: "$env.CHANGE_ID"

                        // println "ret name: ${jobName}"
                    }
                }

                container('tkn') {
                    script {
                        try {
                            // println "to pass ret name: ${jobName}"
                            tekton.print_log(jobName)
                        } catch (Exception e) {
                            println e
                        }
                    }
                }
            }
        }
    }
}
