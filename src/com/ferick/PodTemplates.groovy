package com.ferick

class PodTemplates implements Serializable {

    final def steps

    PodTemplates(steps) {
        this.steps = steps
    }

    def jdkTemplate(String containerName, Closure body) {
        steps.podTemplate(containers: [
                steps.containerTemplate(name: containerName, image: 'openjdk:8u232-jdk', ttyEnabled: true, command: 'cat')
        ]) {
            body()
        }
    }

    def jdkTemplateWithPortMapping(String containerName, Integer containerPort, Integer hostPort, Closure body) {
        steps.podTemplate(containers: [
                steps.containerTemplate(name: containerName, image: 'openjdk:8u232-jdk', ttyEnabled: true, command: 'cat',
                        ports: [steps.portMapping(name: 'ports', containerPort: containerPort, hostPort: hostPort)])
        ]) {
            body()
        }
    }

    def dockerTemplate(Closure body) {
        steps.podTemplate(containers: [
                steps.containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat')],
                volumes: [steps.hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]
        ) {
            body()
        }
    }

    def helmTemplate(String secretName, Closure body) {
        steps.podTemplate(containers: [
                steps.containerTemplate(name: 'helm', image: 'lwolf/helm-kubectl-docker', ttyEnabled: true, command: 'cat')],
                volumes: [steps.secretVolume(mountPath: '/etc/helm-repo-certs', secretName: secretName)]
        ) {
            body()
        }
    }
}