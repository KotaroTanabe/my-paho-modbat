#!/bin/bash

BASE=${1%.*}
OUTPUT=${BASE}-color.pdf
TITLE=${BASE##*/}
STEPS=`grep '#' $1 | wc -l`
gnuplot -e "set term pdfcairo enhanced; INPUT='$1'; OUTPUT='$OUTPUT'; TITLE='$TITLE'; STEPS=$STEPS" house.plt

