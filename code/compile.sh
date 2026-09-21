#!/bin/bash

JAVAFX_PATH="libs/javafx"

# Criar a pasta bin se não existir
mkdir -p bin

# Compilar o código para dentro de bin/
javac \
  --module-path "$JAVAFX_PATH" \
  --add-modules javafx.controls,javafx.graphics \
  -cp "libs/*" \
  -d bin \
  $(find src -name "*.java")


