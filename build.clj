(ns build
  (:refer-clojure :exclude [test])
  (:require
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))


(def lib 'org.sqids/sqids-clojure)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")


(defn test
  "Run all the tests."
  [opts]
  (let [{:keys [exit]} (b/process {:command-args ["bin/kaocha"]})]
    (when-not (zero? exit)
      (throw (ex-info "Tests failed" {}))))
  opts)


(defn- pom-template
  [version]
  [[:description "FIXME: my new library."]
   [:url "https://github.com/sqids/sqids-clojure"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://choosealicense.com/licenses/mit/"]]]
   [:developers
    [:developer
     [:name "Rob Hanlon"]]]
   [:scm
    [:url "https://github.com/sqids/sqids-clojure"]
    [:connection "scm:git:https://github.com/sqids/sqids-clojure.git"]
    [:developerConnection "scm:git:ssh:git@github.com:sqids/sqids-clojure.git"]
    [:tag (str "v" version)]]])


(defn- jar-opts
  [opts]
  (assoc opts
         :lib lib   :version version
         :jar-file  (format "target/%s-%s.jar" lib version)
         :basis     (b/create-basis {})
         :class-dir class-dir
         :target    "target"
         :src-dirs  ["src"]
         :pom-data  (pom-template version)))


(defn ci
  "Run the CI pipeline of tests (and build the JAR)."
  [opts]
  (test opts)
  (b/delete {:path "target"})
  (let [opts (jar-opts opts)]
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println "\nBuilding JAR..." (:jar-file opts))
    (b/jar opts))
  opts)


(defn install
  "Install the JAR locally."
  [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)


(defn deploy
  "Deploy the JAR to Clojars."
  [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
