package com.ferick

class Helm implements Serializable {

    final def script

    Helm(script) {
        this.script = script
    }

    def helmDeploy(String chartRepo, String chartName, String chartVersion, Map overridingValues = [:]) {
        def params = getParamsAsString(overridingValues)
        script.withKubeConfig(credentialsId: 'jenkinskubeid', serverUrl: "https://${script.params.CLUSTER}", namespace: "${script.params.NAMESPACE}") {
            script.sh 'helm init --client-only'
            script.withCredentials([script.usernamePassword(credentialsId: 'harborId', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                script.sh """helm repo add --ca-file /etc/helm-repo-certs/ca.crt --username=${script.env.USERNAME} --password=${script.env.PASSWORD} \
                            ${chartRepo} https://${script.params.REGISTRY}/chartrepo/${script.params.HELM_RELEASE_NAME}"""
                script.sh 'helm repo update'
                script.sh """helm upgrade ${script.params.HELM_RELEASE_NAME} --install --ca-file /etc/helm-repo-certs/ca.crt \
                            --username=${script.env.USERNAME} --password=${script.env.PASSWORD} ${params}--version ${chartVersion} ${chartRepo}/${chartName}"""
            }
        }
    }

    def helmPush(String chartRepo, String chartName, String chartVersion) {
        script.sh 'apk add git'
        script.sh 'helm plugin install https://github.com/chartmuseum/helm-push'
        script.withCredentials([script.usernamePassword(credentialsId: 'harborId', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            script.sh """helm repo add --ca-file /etc/helm-repo-certs/ca.crt --username=${script.env.USERNAME} --password=${script.env.PASSWORD} \
                            ${chartRepo} https://${script.params.REGISTRY}/chartrepo/${script.params.HELM_RELEASE_NAME}"""
            script.sh 'helm repo update'
            script.sh """helm push --ca-file /etc/helm-repo-certs/ca.crt --username=${script.env.USERNAME} --password=${script.env.PASSWORD} \
                            ${chartName}-${chartVersion}.tgz ${chartRepo}"""
        }
    }

    private String getParamsAsString(Map overridingValues) {
        StringBuilder builder = new StringBuilder()
        if (!overridingValues.isEmpty()) {
            for (entry in overridingValues) {
                builder.append("--set ${entry.key}=${entry.value} ")
            }
        }
        return builder.toString()
    }
}