.PHONY: all test lint install deploy clean

SHELL = /bin/bash
CLOJARS_USERNAME ?= cch1
srcfiles = $(shell find src/ -type f -name '*.clj' -or -name '*.cljc' -or -name '*.cljs' -or -name '*.edn')
testfiles = $(shell find test/ -type f -name '*.clj' -or -name '*.cljc' -or -name '*.cljs' -or -name '*.edn')

all: test pom.xml janus.jar

test: tmp/test-clj tmp/test-cljs

tmp/test-clj: deps.edn $(testfiles) $(srcfiles)
	clojure -M:project/test-clj
	touch tmp/test-clj

tmp/test-cljs: deps.edn $(testfiles) $(srcfiles)
	clojure -M:project/test-cljs
	touch tmp/test-cljs

lint: tmp/lint

tmp/lint: $(srcfiles)
	-clojure -M:lint/kondo
	touch tmp/lint

pom.xml: deps.edn
ifdef UPDATE
	clojure -M:project/pom -t $(UPDATE)
else
	clojure -M:project/pom
endif

janus.jar: pom.xml $(srcfiles)
	clojure -X:project/jar

install: janus.jar
	clojure -X:project/install

deploy: janus.jar
	env CLOJARS_USERNAME=$(CLOJARS_USERNAME) CLOJARS_PASSWORD=$(CLOJARS_PASSWORD) clj -M:project/deploy janus.jar

clean:
	rm -f pom.xml pom.xml.asc janus.jar
	rm -rf target/*
	rm -rf cljs-test-runner-out
