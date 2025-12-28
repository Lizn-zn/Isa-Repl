#!/bin/bash
SCRIPT_DIR="$(dirname $0)"
cd $SCRIPT_DIR/scala-isabelle
sbt publishLocal
cd -
mill compile
mill mill.bsp.BSP/install