#!/bin/bash

# Wrapper script to run the e-commerce platform test runner
# This script activates the virtual environment and runs the test runner

cd "$(dirname "$0")"
source venv/bin/activate
python3 test_runner.py "$@"
