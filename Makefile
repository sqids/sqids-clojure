.PHONY: get-jar test fmt

get-jar:
	mkdir -p vendor
	curl 'https://s01.oss.sonatype.org/content/repositories/snapshots/org/sqids/sqids/0.1.0-SNAPSHOT/sqids-0.1.0-20231127.135843-1.jar' -o vendor/sqids.jar

fmt:
	clojure-lsp format

test:
	clj -M:test
