def call(def script) {
    script.telegramSend("""Build '${script.env.JOB_NAME}'
                         | number '${script.env.BUILD_NUMBER}'
                         | is ${script.currentBuild.currentResult}""".stripMargin())
}