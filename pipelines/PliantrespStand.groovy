import com.ferick.RunTests
import com.ferick.PodTemplates
import com.ferick.Helm

def runTests = new RunTests(this)
def slaveTemplates = new PodTemplates(steps)
def helm = new Helm(this)
def buildContainer = 'build'
def emulatorContainer = 'emulator'
def testingContainer = 'testing'
def emulatorPort = 4567

properties([
        parameters([
                string(defaultValue: '0.1', description: 'Pliantresp emulator version', name: 'EMULATOR_VERSION', trim: true),
                string(defaultValue: '192.168.77.15:31299', description: 'Cloud-native registry URL', name: 'REGISTRY', trim: true),
                string(defaultValue: 'pliantresp/pliantresp', description: 'Emulator docker image', name: 'IMAGE', trim: true),
                string(defaultValue: '0.1', description: 'Emulator docker image tag', name: 'TAG', trim: true),
                string(defaultValue: '192.168.77.15:6443', description: 'Kubernetes cluster URL', name: 'CLUSTER', trim: true),
                string(defaultValue: 'default', description: 'Cluster namespace', name: 'NAMESPACE', trim: true),
                string(defaultValue: 'pliantresp', description: 'Emulator helm release name', name: 'HELM_RELEASE_NAME', trim: true),
                string(defaultValue: '0.1.0', description: 'Emulator helm chart version', name: 'CHART_VERSION', trim: true)
        ])
])

currentBuild.displayName = "${env.BUILD_NUMBER}. ${params.HELM_RELEASE_NAME} ${params.TAG}/${params.CHART_VERSION}"

timestamps {
    slaveTemplates.jdkTemplate(buildContainer) {
        slaveTemplates.jdkTemplateWithPortMapping(emulatorContainer, emulatorPort, emulatorPort) {
            slaveTemplates.jdkTemplate(testingContainer) {
                node(POD_LABEL) {
                    container(buildContainer) {
                        stage('Source') {
                            git 'https://github.com/Cuchillon/pliantresp_work.git'
                        }
                        stage('Build server') {
                            dir('standalone') {
                                sh 'chmod +x ../gradlew'
                                sh '../gradlew clean shadowJar'
                                stash name: 'dockerfile', includes: 'Dockerfile'
                                dir('build/libs') {
                                    stash name: 'emulator', includes: '*.jar'
                                }
                            }
                        }
                        stage('Build client') {
                            dir('client') {
                                sh '../gradlew clean shadowJar'
                                dir('build/libs') {
                                    stash name: 'client-library', includes: '*.jar'
                                }
                            }
                        }
                    }
                    stage('Testing') {
                        def end = false
                        parallel (
                                emulator: { container(emulatorContainer) {
                                    stage('Emulator') {
                                        unstash name: 'emulator'
                                        sh "java -jar ./pliantresp-${params.EMULATOR_VERSION}-standalone.jar &"
                                        waitUntil {
                                            end
                                        }
                                    }
                                }},
                                testing: { container(testingContainer) {
                                    stage('API tests') {
                                        git credentialsId: 'githubCreds', url: 'https://github.com/Cuchillon/test-pliantresp.git'
                                        dir('src/main/resources') {
                                            sh 'rm *.jar'
                                            unstash name: 'client-library'
                                        }
                                        sh 'chmod +x gradlew'
                                        try {
                                            sh './gradlew test'
                                        } finally {
                                            runTests.publishHTMLReport(
                                                    'build/reports/tests/test',
                                                    'index.html',
                                                    'API testling results')
                                            end = true
                                        }
                                    }
                                }}
                        )
                    }
                }
            }
        }
    }
    slaveTemplates.dockerTemplate {
        node(POD_LABEL) {
            container('docker') {
                stage('Docker build') {
                    unstash name: 'emulator'
                    unstash name: 'dockerfile'
                    docker.withRegistry("https://${params.REGISTRY}", 'harborId') {
                        docker.build("${params.REGISTRY}/${params.IMAGE}:${params.TAG}").push("${params.TAG}")
                    }
                }
            }
        }
    }
    slaveTemplates.helmTemplate('ca-secret') {
        node(POD_LABEL) {
            container('helm') {
                stage('Helm deploy') {
                    def pliantrespRepo = "${params.HELM_RELEASE_NAME}repo"
                    def pliantrespChart = "${params.HELM_RELEASE_NAME}-chart"
                    helm.helmDeploy(pliantrespRepo, pliantrespChart, params.CHART_VERSION)
                }
            }
        }
    }
}