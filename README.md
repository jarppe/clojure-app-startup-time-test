# Experimenting Clojure app startup speed

The purpose of this test is to compare the startup time of large clojure
application. Following tests are compared:

- Packaging clojure sources in JAR
- AOT compiling clojure to Java classes
- An über JAR that contains AOT compiled classes and all dependencies
- The über JAR with Leyden CDS archive
- GraalVM native image

I wanted to see what effect the different test cases have on class loading
only. For this purpose I generated the test application in a way that it
tries to eliminate all other factors and emphasize the class loading time.
The application sources are generated so that I create 200 namespaces with
each having 400 functions. Each function accepts a long and returns the value
increased by 1. At the start time I pass 0 to first function and it's return
value to next function an so on.

When clojure function is compiled to Java bytecode, each function is compiled
into a separate class. This means that at the startup time the application
requires the VM to load 80k small classes.

Generating 80k classes is a bit extreme, but it emphasizes nicely the effect
the tests cases have on class loading.

## Running tests

You need `git` and `babashka` to run the tests. Clone this repo and
execute:

```bash
$ bb init
```

This performs following steps:

- Install the Leyden build of the Java 24
- Install GraalVM 23
- Generate the test application sources
- Package the test app to src JAR
- Package the test app to AOT JAR
- Package the test app to src über JAR
- Generate CDS archive for Leyden
- Generate GraalVM native image
- Test the generated artifacts
- Run performance tests

## TL;DR:

On my 2021 Apple Mac Book Pro M1 MAX the results are:

```
src-jar     : 39.260 sec
aot-jar     :  6.937 sec
uber-jar    :  6.981 sec
cds-jar     :  1.039 sec
graalvm     :  0.007 sec
```

The Leyden CDS seems really promising as it reduces the startup time from ~7 sec
to ~1 sec.

The GraalVM image is really fast to start, but also most difficult to produce.
This simple application does not have any dependencies (other than Clojure
standard lib) it's pretty simple, but when you add dependencies it gets
more complicated.

## Links

- [Project Leyden](https://openjdk.org/projects/leyden/)
- [GraalVM](https://www.graalvm.org/)
- [Clojure](https://clojure.org/)
