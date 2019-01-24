#!/bin/bash

echo "$@" | sort | xargs -n 2 ./house-compare.sh
