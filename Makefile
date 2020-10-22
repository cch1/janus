.PHONY: all test test-clj test-cljs lint install deploy clean

SHELL = /bin/bash
CLOJARS_USERNAME = cch1
SOURCE = $(shell find src/ -type f -name '*.clj' -or -name '*.cljc' -or -name '*.cljs' -or -name '*.edn')

all: test pom.xml janus.jar

test: test-clj test-cljs

test-clj:
	clojure -M:project/test-clj

test-cljs:
	clojure -M:project/test-cljs

lint: tmp/lint

tmp/lint: $(SOURCE)
	-clojure -M:lint/kondo
	touch tmp/lint

pom.xml: deps.edn
ifdef UPDATE
	clojure -M:project/pom -t $(UPDATE)
else
	clojure -M:project/pom
endif

janus.jar: pom.xml $(SOURCE)
	clojure -X:project/jar

install: pom.xml janus.jar
	clojure -X:project/install

deploy: janus.jar
	env CLOJARS_USERNAME=$(CLOJARS_USERNAME) CLOJARS_PASSWORD=$(CLOJARS_PASSWORD) clj -M:project/deploy janus.jar

clean:
	rm -f pom.xml pom.xml.asc janus.jar
	rm -rf target/*
	rm -rf cljs-test-runner-out
