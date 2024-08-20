#!/bin/bash

for ((i = 0 ; i < 5000 ; i++ )); do
    fortune -s | tr '\n' ' ' >> quotes.txt
    echo >> quotes.txt
done
