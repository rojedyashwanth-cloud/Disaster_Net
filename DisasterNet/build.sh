#!/usr/bin/env bash
#
# DisasterNet build, test and run.
#
#   ./build.sh            compile everything into out/
#   ./build.sh test       compile, then run every module test
#   ./build.sh run        compile, then run the pipeline over data/ into output/
#   ./build.sh menu       compile, then start the interactive menu
#   ./build.sh all        compile, test, then run the pipeline
#   ./build.sh clean      remove out/
#
# Any argument to test is treated as a name filter, so "./build.sh test Brent"
# runs only the tests whose class name contains "Brent".
set -euo pipefail

cd "$(dirname "$0")"

BUILD_DIR="out"
TEST_DIR="tests"
DATA_DIR="${DATA_DIR:-data}"
OUTPUT_DIR="${OUTPUT_DIR:-output}"

# -Xlint:all is on deliberately. The project is a teaching artifact, so a
# warning that a reader will see in their own build is worth fixing at the source
# rather than suppressing.
JAVAC_FLAGS=(--release 24 -Xlint:all -encoding UTF-8)

sources() {
  find . -name '*.java' -not -path "./${BUILD_DIR}/*"
}

compile() {
  echo "==> compiling into ${BUILD_DIR}"
  rm -rf "${BUILD_DIR}"
  mkdir -p "${BUILD_DIR}"
  # shellcheck disable=SC2046
  javac "${JAVAC_FLAGS[@]}" -d "${BUILD_DIR}" $(sources)
  echo "    $(find "${BUILD_DIR}" -name '*.class' | wc -l | tr -d ' ') class files"
}

run_tests() {
  echo "==> running tests"
  java -cp "${BUILD_DIR}" TestRunner "${BUILD_DIR}" "$@"
}

run_pipeline() {
  echo "==> running the pipeline"
  java -cp "${BUILD_DIR}" Main --pipeline "${DATA_DIR}" "${OUTPUT_DIR}"
}

run_menu() {
  echo "==> starting the interactive menu"
  java -cp "${BUILD_DIR}" Main "${DATA_DIR}" "${OUTPUT_DIR}"
}

case "${1:-all}" in
  compile)
    compile
    ;;
  test)
    compile
    shift || true
    run_tests "$@"
    ;;
  run)
    compile
    run_pipeline
    ;;
  menu)
    compile
    run_menu
    ;;
  all)
    compile
    run_tests
    run_pipeline
    ;;
  clean)
    rm -rf "${BUILD_DIR}"
    echo "==> removed ${BUILD_DIR}"
    ;;
  *)
    echo "usage: $0 [compile|test|run|menu|all|clean] [test-name-filter ...]" >&2
    exit 2
    ;;
esac
