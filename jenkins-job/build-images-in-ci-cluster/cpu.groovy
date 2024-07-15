@Library('jenkins-shared-library') _

def pod = libraryResource 'io/milvus/pod/tekton.yaml'
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
                                                              arch:'amd64'
                                                              // suppress_suffix_of_image_tag: true
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
