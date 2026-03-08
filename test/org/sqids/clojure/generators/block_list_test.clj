(ns org.sqids.clojure.generators.block-list-test
  (:require
    [clojure.test :as t]))

(t/deftest domain-characters-fallback-test
  (let [block-list-word-characters
        (var-get (requiring-resolve 'org.sqids.clojure.generators.block-list/block-list-word-characters))
        domain-characters
        (requiring-resolve 'org.sqids.clojure.generators.block-list/domain-characters)]
    (t/is (= block-list-word-characters
             (domain-characters "")))))
