#!/bin/bash

BASE=${1%.*}
OUTPUT=${BASE}-mono.pdf
TITLE=${BASE##*/}
STEPS=`grep '#' $1 | wc -l`
gnuplot -e "set term pdfcairo enhanced; set palette gray; INPUT='$1'; OUTPUT='$OUTPUT'; TITLE='$TITLE'; STEPS=$STEPS" house.plt

