#!/bin/sh
BASEDIR=$1
DRUIDVERSION=$2
sed -e "s/DRUIDVERSION/${DRUIDVERSION}/" ${BASEDIR}/README.template > ${BASEDIR}/README