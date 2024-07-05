@Library('jenkins-shared-library') _

def pod = libraryResource 'io/milvus/pod/tekton-3.yaml'
def name = ''

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
                        job_name = tekton.run revision: "$env.BRANCH_NAME", \
                                                          arch:'amd64', \
                                                          computing_engine:'gpu',
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
                        (ok, ret) = tekton.check_result(job_name)
                        if (!ok) {
                            error(ret)
                        }

                        println ret
                    }
                }
            }
        }

        stage('archive result') {
            steps {
                container('jnpl') {
                    script {
                        output.image = ret

                        writeJSON(file: 'output.json', json: output)
                        archiveArtifacts artifacts: 'output.json', onlyIfSuccessful: true
                    }
                }
            }
        }
    }
}
