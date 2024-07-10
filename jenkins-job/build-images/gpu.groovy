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

                        output.image = tekton.check_result(job_name)
                    }
                }
            }
        }

        stage('archive result') {
            steps {
                container('jnpl') {
                    script {

                        tekton.archive(output)
                    }
                }
            }
        }
    }
}
