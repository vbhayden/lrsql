#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -J-Dfile.encoding=UTF-8 -server -cp lrsql.jar lrsql.postgres.main $@
