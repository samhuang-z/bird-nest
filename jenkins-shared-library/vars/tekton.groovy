
def run(Map args) {
    def getGcc = { revision, gcc ->
        if (revision.startsWith('v2.3') || revision.startsWith('2.3')) {
            'gcc9'
        } else {
            gcc ?: 'gcc12'
        }
    }

    def getOSName = { revision, osName ->
        if (revision.startsWith('v2.3') || revision.startsWith('2.3')) {
            'ubuntu20.04'
        } else {
            osName ?: ''
        }
    }




    def part_of_arm_template = '''
  taskRunTemplate:
    podTemplate:
      tolerations:
      - key: node-role.kubernetes.io/knowhere
        operator: "Exists"
        effect: NoSchedule
      - key: "node-role.kubernetes.io/arm"
        operator: "Exists"
        effect: "NoSchedule"
      nodeSelector:
        "kubernetes.io/arch": "arm64"
'''

    def template = """
cat << EOF | kubectl create -f -
apiVersion: tekton.dev/v1
kind: PipelineRun
metadata:
  generateName: milvus-build-
  namespace: milvus-ci
spec:
  timeouts:
    pipeline: "1h30m"
  pipelineRef:
    name: ${choose_pipeline(args)}
  taskRunSpecs:
    - pipelineTaskName: fetch-source
      podTemplate:
        securityContext:
          fsGroup: 65532 
    - pipelineTaskName: image-build-push
      serviceAccountName: robot-tekton
    - pipelineTaskName: sync-env-image
      serviceAccountName: robot-tekton
${ args.arch == 'arm64' ? part_of_arm_template : '' }
  workspaces:
  - name: shared-data
    volumeClaimTemplate:
      spec:
        storageClassName: ${ args.storage_class ?: 'local-path' }
        accessModes:
        - ReadWriteOnce
        resources:
          requests:
            storage: 100Gi
  params:
${ part_of_code_fetch(args) }
  - name: arch
    value: ${args.arch}
  - name: computing_engine
    value: ${args.computing_engine ?: 'cpu'}
  - name: build_type
    value: ${args.build_type == 'debug' ? 'debug' : 'release'}
  - name: repo_url
    value: ${args.repo_url ?: 'https://github.com/milvus-io/milvus.git'}
  - name: milvus_repo_owner
    value: ${args.milvus_repo_owner ?: 'milvus-io'}
  - name: suppress_suffix_of_image_tag
    # if revision starts with 'v' (means tag), then image tag should be end with arch(amd64, arm64)
    # otherwise, arch suffix could be suppressed
    value: ${suppress_suffix_of_image_tag(args)}
  - name: os_name
    value: ${getOSName(args.revision, args.os_name)}
  - name: milvus_env_version
    value: ${args.milvus_env_version ?: ''}
  - name: gcc
    value: ${getGcc(args.revision, args.gcc)}
  - name: registryToPush
    value: ${args.registryToPush ?: 'harbor.milvus.io'}
  - name: additional-make-params
    value: ${args.additional_make_params ?: 'use_disk_index=ON'}
EOF
"""

    println template

    def ret = sh( label: 'tekton pipeline run', script: template, returnStdout: true)

    name = ret.split('/')[1].split(' ')[0]
    return name
}

def print_log(name) {
    sh """
     timeout 1h tkn pipelinerun logs ${name} -f -n milvus-ci
  """
}

// return image if there is no failures
// throw exception if there is a failure
def check_result(name) {
    def ret = sh( label: 'tekton pipelinerun describe', script: "tkn pipelinerun describe ${name} -n milvus-ci -o yaml", returnStdout: true)

    println ret

    def read = readYaml text: ret
    // mean failed if there is a condition with type Succeeded and status False
    def failures = read.status.conditions.findAll { it.type == 'Succeeded' && it.status == 'False' }

    if (failures.size() > 0) {
        throw new Exception(failures[0].message)
    }

    // get first element if any result found
    def query = { list, closure  ->

        def items = list.findAll(closure)

        if (items) {
            items[0].value
        }
    }

    def image = new Image()
    image.fqdn = query(read.status.results) { it.name == 'image-fqdn' }
    image.tag = query(read.status.results) { it.name == 'image-tag' }

    return image
}


class Image {
    String fqdn
    String tag
}

def archive(output){
    writeJSON(file: 'output.json', json: output)
    archiveArtifacts artifacts: 'output.json', onlyIfSuccessful: true
}

def part_of_code_fetch(Map args) {
  if (args.isPr) {
"""
  - name: pullRequestBaseRef
    value: ${args.pullRequestBaseRef}
  - name: pullRequestNumber
    value: ${args.pullRequestNumber}
"""
  } else {

"""
  - name: revision
    value: ${args.revision}
  - name: refspec
    value: ${args.revision.startsWith('v') ? "refs/tags/${args.revision}:refs/tags/${args.revision}" : "refs/heads/${args.revision}:refs/heads/${args.revision}" }
"""
  }
}


def choose_pipeline (Map args ) {
    if (args.isPr) {
        'milvus-build-for-pull-request'
    } else {
        'milvus-clone-build-push'
  }
}

def suppress_suffix_of_image_tag(Map args) {

  if (args.revision && args.revision.startsWith('v')) {
    return false
  }


  return args.suppress_suffix_of_image_tag ?: false
}
