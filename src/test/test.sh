#!/bin/bash

# Define the paths
EXPECTED_DIR="src/test"

test_lst=(
    "Test_IsaRepl"
    "Test_Smt"
    "Test_SmtRepl"
    "Test_Hammer"
    "Test_HammerRepl"
)   

for test in "${test_lst[@]}"; do
    # concate the cmd
    cmd="runMain RunIsar.$test"
    # print the name of infile
    # Extract the base filename without the extension
    base=$(basename "$test")

    # Define the path for the expected output file
    outputfile="$EXPECTED_DIR/$base.out"

    # Run the command and store its output in a temporary file
    sbt "$cmd" > $outputfile 2>&1

    # Check whether success is in the output file
    if grep -q "success" "$outputfile"; then
        echo "$base: PASSED"
    else
        echo "$base: FAILED, check $outputfile for details"
        exit 1
    fi

done
