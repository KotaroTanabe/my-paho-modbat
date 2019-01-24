#!/bin/bash

BASE=${1%.*}
OUTPUT=${BASE}.gif
TITLE=${BASE##*/}
STEPS=`grep '#' $1 | wc -l`
gnuplot -e "set term gif animate optimize delay 20 size 640,640; INPUT='$1'; OUTPUT='$OUTPUT'; TITLE='$TITLE'; STEPS=$STEPS" house.plt

