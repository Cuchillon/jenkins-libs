import com.ferick.RunTests
import com.ferick.PodTemplates
import com.ferick.Helm

def runTests = new RunTests(this)
def slaveTemplates = new PodTemplates(steps)
def helm = new Helm(this)
def buildContainer = 'build'

properties([
        parameters([
                string(defaultValue: '192.168.77.15:31299', description: 'Cloud-native registry URL', name: 'REGISTRY', trim: true),
                string(defaultValue: 'sk/sk-backend', description: 'Backend docker image', name: 'BACKEND_IMAGE', trim: true),
                string(defaultValue: 'v1', description: 'Backend docker image tag', name: 'BACKEND_TAG', trim: true),
                string(defaultValue: 'sk/sk-database', description: 'Database docker image', name: 'DB_IMAGE', trim: true),
                string(defaultValue: 'v1', description: 'Database docker image tag', name: 'DB_TAG', trim: true),
                string(defaultValue: '192.168.77.15:6443', description: 'Kubernetes cluster URL', name: 'CLUSTER', trim: true),
                string(defaultValue: 'default', description: 'Cluster namespace', name: 'NAMESPACE', trim: true),
                string(defaultValue: 'sk', description: 'SK-service helm release name', name: 'HELM_RELEASE_NAME', trim: true),
                string(defaultValue: '0.1.0', description: 'SK-service helm chart version', name: 'CHART_VERSION', trim: true),
                string(defaultValue: '127.0.0.1:8089', description: 'SK-service license agent address', name: 'LICENSE_ADDRESS', trim: true)
        ])
])

currentBuild.displayName = "${env.BUILD_NUMBER}. ${params.HELM_RELEASE_NAME} chart:${params.CHART_VERSION}"

timestamps {
    slaveTemplates.gradleTemplate(buildContainer) {
        slaveTemplates.dockerTemplate {
            node(POD_LABEL) {
                container(buildContainer) {
                    stage('Source') {
                        git credentialsId: 'githubCreds', url: 'https://github.com/Cuchillon/sk-service.git'
                        dir('database') {
                            stash name: 'dockerfile-database', includes: 'Dockerfile'
                        }
                        dir('testutils') {
                            stash name: 'chart', includes: 'sk-chart/**'
                        }
                    }
                    stage('Backend build') {
                        dir('backend') {
                            stash name: 'dockerfile-backend', includes: 'Dockerfile'
                            sh 'gradle war'
                            dir('build/libs') {
                                stash name: 'backend', includes: 'sk.war'
                            }
                        }
                    }
                }
                container('docker') {
                    stage('Docker backend') {
                        unstash name: 'backend'
                        unstash name: 'dockerfile-backend'
                        docker.withRegistry("https://${params.REGISTRY}", 'harborId') {
                            docker.build("${params.REGISTRY}/${params.BACKEND_IMAGE}:${params.BACKEND_TAG}").push("${params.BACKEND_TAG}")
                        }
                    }
                    stage('Docker database') {
                        unstash name: 'dockerfile-database'
                        docker.withRegistry("https://${params.REGISTRY}", 'harborId') {
                            docker.build("${params.REGISTRY}/${params.DB_IMAGE}:${params.DB_TAG}").push("${params.DB_TAG}")
                        }
                    }
                }
            }
        }
    }
    slaveTemplates.helmTemplate('ca-secret') {
        node(POD_LABEL) {
            container('helm') {
                def skRepo = "${params.HELM_RELEASE_NAME}-repo"
                def skChart = "${params.HELM_RELEASE_NAME}-chart"
                stage('Chart package') {
                    unstash name: 'chart'
                    sh 'helm init --client-only'
                    sh 'helm package ./sk-chart'
                    helm.helmPush(skRepo, skChart, params.CHART_VERSION)
                }
                stage('Helm deploy') {
                    Map values = [
                            'backend.image.repository' : "${params.REGISTRY}/${params.BACKEND_IMAGE}",
                            'backend.image.tag' : "${params.BACKEND_TAG}",
                            'database.image.repository' : "${params.REGISTRY}/${params.DB_IMAGE}",
                            'database.image.tag' : "${params.DB_TAG}",
                            'backend.environment.license.agentAddress' : "${params.LICENSE_ADDRESS}"
                    ]
                    helm.helmDeploy(skRepo, skChart, params.CHART_VERSION, values)
                }
            }
        }
    }
}