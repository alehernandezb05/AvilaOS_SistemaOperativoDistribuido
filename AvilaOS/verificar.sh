#!/bin/sh
set -eu
cd "$(dirname "$0")"
"${MVN:-mvn}" -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath-pruebas.txt
java -cp "target/classes:target/test-classes:$(cat target/classpath-pruebas.txt)" edu.unimet.avilaos.PruebaUnComputador "$@"
