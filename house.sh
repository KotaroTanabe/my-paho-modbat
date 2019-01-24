#!/bin/bash

for dat in "$@"
do
    ./house-gif.sh $dat
    ./house-color.sh $dat
    ./house-mono.sh $dat
done

./house-compare-all.sh "$@"
./house-scatter.sh "$@"
