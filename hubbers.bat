@echo off
set HUBBERS_REPO=hubbers-repo\src\main\resources\repo
java -cp hubbers-distribution\target\hubbers.jar org.hubbers.Main --repo %HUBBERS_REPO% %* 
