@echo off
echo ********************
echo make.cmd all
echo make.cmd clean
echo make.cmd app_all
echo make.cmd app_clean:
echo ********************
set CUR_PATH=%CD%
cd DddScanLaserCtrl
PATH=%CUR_PATH%\bin\
make.exe -fGNUmakefile %1