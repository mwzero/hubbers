@echo off
set HUBBERS_REPO=.\repo
echo java -cp target\hubbers-0.1.0-SNAPSHOT.jar org.hubbers.Main %*
target\hubbers.exe %* 
