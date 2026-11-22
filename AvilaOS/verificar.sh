#!/bin/sh
set -eu
cd "$(dirname "$0")"
"${MVN:-mvn}" -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath-pruebas.txt
classpath="target/classes:target/test-classes:$(cat target/classpath-pruebas.txt)"
if [ "${1:-}" = disco ]; then
    shift
    java -cp "$classpath" edu.unimet.avilaos.PruebaDisco "$@"
else
    java -cp "$classpath" edu.unimet.avilaos.PruebaUnComputador "$@"
    if [ "$#" -eq 0 ]; then
        java -cp "$classpath" edu.unimet.avilaos.PruebaDisco
    fi
fi
