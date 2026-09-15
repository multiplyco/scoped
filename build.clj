(ns build
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as deploy]))


(def lib 'co.multiply/scoped)
(def version "0.1.18")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def runtime-jar "target/scoped-runtime.jar")
(def versioned-class-dir (str class-dir "/META-INF/versions/25"))
(def basis (delay (b/create-basis {:project "deps.edn"})))


(defn clean
  [_]
  ;; Preserve development benchmark records under target/bench across builds.
  (doseq [path [class-dir jar-file runtime-jar]]
    (b/delete {:path path})))


(defn version-str
  "Print the current version string."
  [_]
  (println version))


(def scm-url "https://github.com/multiplyco/scoped")


(defn- validate-runtime-api!
  []
  (let [jar-tool (io/file (System/getProperty "java.home") "bin"
                   (if (= java.io.File/separator "\\") "jar.exe" "jar"))
        result (b/process {:command-args [(str jar-tool) "--validate" "--file" runtime-jar]})]
    (when-not (zero? (:exit result))
      (throw (ex-info "Multi-release runtime implementations have incompatible Java APIs" result)))))


(defn compile-java
  "Build the Java-only multi-release JAR used by source/REPL/benchmark classpaths."
  [_]
  (when (< (.major (Runtime/version)) 25)
    (throw (ex-info "Building scoped requires JDK 25+" {})))
  (b/delete {:path class-dir})
  (b/delete {:path runtime-jar})
  (b/javac {:src-dirs ["src"] :class-dir class-dir :basis @basis
            :javac-opts ["--release" "17" "-Xlint:all"]})
  (b/javac {:src-dirs ["src-java/jdk25"] :class-dir versioned-class-dir :basis @basis
            :javac-opts ["--release" "25" "-Xlint:all"]})
  (b/jar {:class-dir class-dir :jar-file runtime-jar
          :manifest {"Multi-Release" "true"}})
  (validate-runtime-api!))


(defn jar
  [_]
  (clean nil)
  (compile-java nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     @basis
                :src-dirs  ["src"]
                :scm       {:url                 scm-url
                            :connection          (str "scm:git:" scm-url)
                            :developerConnection (str "scm:git:" scm-url)
                            :tag                 (str "v" version)}
                :pom-data  [[:licenses
                             [:license
                              [:name "Eclipse Public License 2.0"]
                              [:url "https://www.eclipse.org/legal/epl-2.0/"]]]]})
  (b/copy-dir {:src-dirs   ["src"]
               :include "**.{clj,cljc,cljs}"
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file
          :manifest {"Multi-Release" "true"}})
  (println "Built:" jar-file))


(defn compile-representation
  "Compile the standalone scope representation experiment, outside the library JAR."
  [_]
  (let [output "target/bench/representation/classes"]
    (b/delete {:path output})
    (b/javac {:src-dirs ["bench/experiments/representation/java"]
              :class-dir output :basis (b/create-basis {:aliases [:bench-representation]})
              :javac-opts ["--release" "17" "-Xlint:all"]})))


(defn compile-task-slot
  "Compile the isolated Java 25 task-slot experiment, outside the library JAR."
  [_]
  (let [output "target/bench/task-slot/classes"]
    (b/delete {:path output})
    (b/javac {:src-dirs ["bench/experiments/task-slot/java"]
              :class-dir output :basis (b/create-basis {:aliases [:bench-task-slot]})
              :javac-opts ["--release" "25" "-Xlint:all"]})))


(defn compile-bifurcan
  "Compile the standalone Bifurcan comparison, outside the library JAR."
  [_]
  (let [output "target/bench/bifurcan/classes"]
    (b/delete {:path output})
    (b/javac {:src-dirs ["bench/experiments/bifurcan/java"]
              :class-dir output :basis (b/create-basis {:aliases [:bench-bifurcan]})
              :javac-opts ["--release" "17" "-Xlint:all"]})))


(defn- java-major
  [home]
  (let [release (io/file home "release")]
    (when (.isFile release)
      (with-open [reader (io/reader release)]
        (let [properties (doto (java.util.Properties.) (.load reader))]
          (some->> (.getProperty properties "JAVA_VERSION")
            (re-find #"\d+") parse-long))))))


(defn- java-command
  [major options]
  (let [setting (keyword (str "jdk" major "-home"))
        variable (str "JAVA" major "_HOME")
        explicit (or (get options setting) (System/getenv variable))
        candidates (if explicit [explicit]
                       (concat [(System/getProperty "java.home")]
                         (sort-by str #(compare %2 %1)
                           (seq (.listFiles (io/file (or (System/getenv "SDKMAN_CANDIDATES_DIR")
                                                       (str (System/getProperty "user.home") "/.sdkman/candidates"))
                                              "java"))))))
        home (first (filter #(= major (java-major %)) candidates))]
    (when-not home
      (throw (ex-info (str "JDK " major " not found; set " variable " or " setting
                        " to its installation directory") {:major major})))
    (.getCanonicalPath (io/file home "bin" (if (= java.io.File/separator "\\") "java.exe" "java")))))


(defn test-jar
  "Build once, then test that exact JAR on JDK 17, JDK 25 and the base runtime on 25.
   Override homes with :jdk17-home / :jdk25-home or JAVA17_HOME / JAVA25_HOME."
  [{:keys [runtimes] :or {runtimes [:jdk17 :jdk25 :thread-local]} :as options}]
  (let [config {:jdk17 {:major 17 :backend "ThreadLocalBackend"}
                :jdk25 {:major 25 :backend "ScopedValueBackend"}
                :thread-local {:major 25 :backend "ThreadLocalBackend" :jar-version 17}}
        _ (when-not (and (seq runtimes) (every? config runtimes))
            (throw (ex-info "Select :runtimes from [:jdk17 :jdk25 :thread-local]" {})))
        ;; Resolve all required installations before doing any build work.
        jobs (mapv #(assoc (config %) :id % :java-cmd (java-command (:major (config %)) options)) runtimes)
        artifact (.getCanonicalPath (io/file jar-file))]
    (jar nil)
    (let [test-basis (b/create-basis
                       {:project (assoc (edn/read-string (slurp "deps.edn")) :paths [artifact])
                        :aliases [:test]})]
      (doseq [{:keys [id java-cmd major backend jar-version]} jobs]
        (println "Testing" artifact "on" id "using" java-cmd)
        (let [result (b/process
                       (b/java-command
                         {:java-cmd java-cmd :basis test-basis :main "clojure.main"
                          :java-opts [(str "-Djdk.util.jar.version=" (or jar-version major))
                                      "-Djdk.util.jar.enableMultiRelease=true"]
                          :main-args ["-m" "co.multiply.scoped-jar-runner" artifact (str major) backend]}))]
          (when-not (zero? (:exit result))
            (throw (ex-info (str "Packaged tests failed: " id) result))))))))


(defn install
  [_]
  (jar nil)
  (b/install {:basis     @basis
              :lib       lib
              :version   version
              :jar-file  jar-file
              :class-dir class-dir})
  (println "Installed:" lib version))


(defn tag
  "Create and push a version tag."
  [_]
  (let [tag (str "v" version)]
    (b/git-process {:git-args ["tag" tag]})
    (b/git-process {:git-args ["push" "origin" tag]})
    (println "Tagged and pushed:" tag)))


(defn deploy
  [_]
  (jar nil)
  (deploy/deploy {:installer  :remote
                  :artifact   jar-file
                  :pom-file   (str class-dir "/META-INF/maven/co.multiply/scoped/pom.xml")
                  :repository {"clojars" {:url      "https://clojars.org/repo"
                                          :username (System/getenv "CLOJARS_DEPLOY_MAVEN_USERNAME")
                                          :password (System/getenv "CLOJARS_DEPLOY_MAVEN_PASSWORD")}}}))
