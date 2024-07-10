@Library('jenkins-shared-library') _

def pod = libraryResource 'io/milvus/pod/tekton.yaml'
def name = ''

def output = [:]
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
        stage('build') {
            steps {
                lock('arm-compute-resource') {
                    container('kubectl') {
                        script {
                            job_name = tekton.run revision: "$env.BRANCH_NAME",
                                                              arch:'arm64',
                                                              datacenter: 'IDC',
                                                              computing_engine:'gpu'
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
