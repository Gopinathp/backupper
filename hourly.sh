#!/bin/bash
. /home/gopinath/.bashrc
cd $HOME
export JAVA_HOME=/home/gopinath/.sdkman/candidates/java/current
export PATH=/usr/local/bin:$PATH
export PATH=/usr/bin:$PATH
export PATH=/home/gopinath/.sdkman/candidates/java/current/bin:$PATH
export PATH=/home/gopinath/.sdkman/candidates/kotlin/current/bin:$PATH
export PATH=/home/gopinath/.sdkman/candidates/kscript/current/bin:$PATH
kscript /home/gopinath/backupper.kts -b prod_backups -d prod_db,client_db -v -r
