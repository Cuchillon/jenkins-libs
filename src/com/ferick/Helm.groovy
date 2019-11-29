package com.ferick

class Helm implements Serializable {

    final def script

    Helm(script) {
        this.script = script
    }

    def helmDeploy(String chartRepo, String chartName, String chartVersion) {
        script.withKubeConfig(credentialsId: 'jenkinskubeid', serverUrl: "https://${script.params.CLUSTER}", namespace: "${script.params.NAMESPACE}") {
            script.sh 'helm init --client-only'
            script.withCredentials([script.usernamePassword(credentialsId: 'harborId', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                script.sh """helm repo add --ca-file /etc/helm-repo-certs/ca.crt --username=${script.env.USERNAME} --password=${script.env.PASSWORD} \
                            ${chartRepo} https://${script.params.REGISTRY}/chartrepo/${script.params.HELM_RELEASE_NAME}"""
                script.sh 'helm repo update'
                script.sh """helm upgrade ${script.params.HELM_RELEASE_NAME} --install --ca-file /etc/helm-repo-certs/ca.crt \
                            --username=${script.env.USERNAME} --password=${script.env.PASSWORD} --version ${chartVersion} ${chartRepo}/${chartName}"""
            }
        }
    }
}