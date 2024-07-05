@Library('jenkins-shared-library') _

def pod = libraryResource 'io/milvus/pod/tekton-3.yaml'
def output = [:]

pipeline {
    options {
        skipDefaultCheckout true
    }
    agent {
        kubernetes {
            yaml pod
        }
    }
    stages {
        stage('build') {
            steps {
                container('kubectl') {
                    script {
                        job_name = tekton.run revision: "$env.BRANCH_NAME",
                                                              arch:'amd64',
                                                              suppress_suffix_of_image_tag: true,
                                                              storage_class: 'gp3',
                                                              registryToPush: 'harbor-us-vdc.zilliz.cc'
                    }
                }

                container('tkn') {
                    script {

                        try {
                           tekton.print_log(job_name)
                        } catch (Exception e) {
                            println e
                        }

                        image = tekton.check_result(job_name)

                    }
                }
            }
        }

        stage('archive result') {
            steps {
                container('jnpl') {
                    script {
                        output.image = image

                        writeJSON(file: 'output.json', json: output)
                        archiveArtifacts artifacts: 'output.json', onlyIfSuccessful: true
                    }
                }
            }
        }
    }
}
