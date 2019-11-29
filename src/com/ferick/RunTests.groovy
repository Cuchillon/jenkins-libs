package com.ferick

class RunTests implements Serializable {

    final def script

    RunTests(script) {
        this.script = script
    }

    def publishHTMLReport(String reportDir, String files, String name) {
        try {
            script.publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: false,
                    keepAll: false,
                    reportDir: reportDir,
                    reportFiles: files,
                    reportName: name
            ])
        } catch(e) {
            script.echo("Can't publish HTML report\n" + e)
        }
    }

    def telegramBot() {
        script.telegramSend("""Build '${script.env.JOB_NAME}'
                             | number '${script.env.BUILD_NUMBER}'
                             | is ${script.currentBuild.currentResult}""".stripMargin())
    }
}