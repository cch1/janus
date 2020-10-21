.PHONY: test test-clj test-cljs install all clean

SHELL = /bin/bash
INCREMENT = MajorMinorPatch
CLOJARS_USERNAME = cch1

all: test pom.xml janus.jar

test: test-clj test-cljs

test-clj:
	clojure -M:project/test-clj

test-cljs:
	clojure -M:project/test-cljs

pom.xml: deps.edn
	clojure -M:project/pom -t $(INCREMENT)

janus.jar: pom.xml
	clojure -X:project/jar

build: pom.xml janus.jar

install: build
	clojure -X:project/install

deploy: build
	env CLOJARS_USERNAME=$(CLOJARS_USERNAME) CLOJARS_PASSWORD=$(CLOJARS_PASSWORD) clj -M:project/deploy janus.jar

clean:
	rm -f pom.xml pom.xml.asc janus.jar
	rm -rf target/*
	rm -rf cljs-test-runner-out
