# labor

## Setup

- add a run config
- run on WSL (when on Windows), java path should be just `/usr`
- build & run -> select java version and MyShell
- when sys is mounted in wsl project path on target should look something like  ``/mnt/c/Program Files/JetBrains/IntelliJ IDEA 2021.3.2/jbr/bin /labor`` (should be set automatically)
- environment variables have to be set (look in source_mich) => ``LD_LIBRARY_PATH=./cTools``
