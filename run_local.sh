#!/bin/bash

sbt "~run -Drun.mode=Dev -Dhttp.port=15503 -Dapplication.router=testOnlyDoNotUseInAppConf.Routes $*"
