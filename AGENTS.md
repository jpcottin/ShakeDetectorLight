This project uses Lightbuild build system.
In order to build it you should use android-cli as `android build` command.
Make sure the `ANDROID_CLI_BUILD=true` env variable is set in order for the
build command to be available. The project structure build file is
project-lightbuild.yaml at the root of the project, with lightbuild.yaml files
in each module.

Run `android help build` to see details of how to use it.
