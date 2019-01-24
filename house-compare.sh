#!/bin/bash

BASE=${1%-*}
DIR=${1%/*}
OUTPUT=${BASE}-compare.gif
TITLE=Comparison
STEPS=`grep '#' $1 | wc -l`
MERGED=${BASE}-merge.dat
paste $1 $2 > "${MERGED}"
gnuplot -e "set term gif animate optimize delay 20 size 800,800; INPUT0='$1'; INPUT1='$2'; OUTPUT='$OUTPUT'; TITLE='$TITLE'; MERGED='$MERGED'; STEPS=$STEPS" house-compare.plt
# gnuplot -e "set term pdfcairo enhanced size 6in, 6in; INPUT0='$1'; INPUT1='$2'; OUTPUT='$OUTPUT'; TITLE='$TITLE'; MERGED='$MERGED'; STEPS=$STEPS" house-compare.plt
rm -f ${MERGED}
