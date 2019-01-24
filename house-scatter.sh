#!/bin/bash

BASE=${1%.*}
DIR=${1%/*}
INPUT=${DIR}/maxdiff.dat
STAT=${DIR}/stat.txt
OUTPUT=${DIR}/maxdiff.pdf
echo "$@" | sort | xargs -n 2 ./house-make-maxdiff.sh ${INPUT} ${STAT}
gnuplot -e "set term pdfcairo enhanced; INPUT='$INPUT'; OUTPUT='$OUTPUT'" house-scatter.plt
rm -f ${INPUT}
