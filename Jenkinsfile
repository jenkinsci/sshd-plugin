// Builds a module using https://github.com/jenkins-infra/pipeline-library
buildPlugin(configurations: buildPlugin.recommendedConfigurations())

node('linux'){
  stage('Funcional tests'){
    env.FUNCIONAL_TESTS_DIR = 'src/test/funcional'
    retry(3){
      sh(label: 'Prepare dependencies', script: "make -C ${FUNCIONAL_TESTS_DIR} dependencies")
      sh(label: 'Start Jenkins container', script: "make -C ${FUNCIONAL_TESTS_DIR} start")
    }
    sh(label: 'Run tests', script: "make -C ${FUNCIONAL_TESTS_DIR} test", returnStatus: true)
    junit(
      allowEmptyResults: true,
      keepLongStdio: true,
      testResults: "${FUNCIONAL_TESTS_DIR}/junit-report.xml"
    )
  }
}
