(ns build
  (:refer-clojure :exclude [test])
  (:require
    [clojure.string :as str]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))

(def lib 'org.sqids/sqids-clojure)

(def versions
  (let [major   1
        minor   0
        commits {:release  (b/git-count-revs nil)
                 :snapshot "9999-SNAPSHOT"}]
    (update-vals commits #(str/join "." [major minor %]))))

(def class-dir "target/classes")

(defn ^:private pom-template
  [version]
  [[:description "Official Clojure port of Sqids. Generate short YouTube-looking IDs from numbers."]
   [:url "https://github.com/sqids/sqids-clojure"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://spdx.org/licenses/MIT.html"]]]
   [:developers
    [:developer
     [:name "Rob Hanlon"]]]
   [:scm
    [:url "https://github.com/sqids/sqids-clojure"]
    [:connection "scm:git:https://github.com/sqids/sqids-clojure.git"]
    [:developerConnection "scm:git:ssh:git@github.com:sqids/sqids-clojure.git"]
    [:tag (str "v" version)]]])

(defn jar-opts
  [opts]
  (let [version (versions (if (:snapshot opts) :snapshot :release))]
    (assoc opts
           :lib       lib
           :version   version
           :jar-file  (format "target/%s-%s.jar" lib version)
           :basis     (b/create-basis {:aliases [:clj]})
           :class-dir class-dir
           :target    "target"
           :src-dirs  ["src"]
           :pom-data  (pom-template version))))

(defn jar
  "Run the CI pipeline of tests (and build the JAR)."
  [opts]
  (b/delete {:path "target"})
  (let [opts (jar-opts opts)]
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
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
    (dd/deploy {:installer :remote
                :artifact  (b/resolve-path jar-file)
                :pom-file  (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
