#!/bin/bash

T0=`grep '#' $3 | tail -1 | cut -d ' ' -f 4`
T1=`grep '#' $4 | tail -1 | cut -d ' ' -f 4`
echo $T0 $T1 >> $1
echo $3 $T0 >> $2
echo $4 $T1 >> $2
