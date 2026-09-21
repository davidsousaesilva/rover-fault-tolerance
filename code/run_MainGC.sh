#!/bin/bash

#10.0.9.20 1025
#0.0.0.0 1025

JAVAFX_PATH="libs/javafx"

java \
  --module-path "$JAVAFX_PATH" \
  --add-modules javafx.controls,javafx.graphics \
  -cp "bin:libs/*" \
  apps.MainGC 10.0.9.20 1025
