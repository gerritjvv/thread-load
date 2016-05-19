(defproject thread-load "0.3.2"
  :description "Load balancing work units over multiple threads, meant for long running threads. "
  :url "https://github.com/gerritjvv/thread-load"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true
                *assert* false}
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
   jvm-opts ["-Xmx1g"]
  :java-source-paths ["java"]
  
  :plugins [
         [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
         [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]
  :dependencies [
    [fun-utils "0.6.1"]
    [com.lmax/disruptor "3.3.0"]
    [org.clojure/tools.logging "0.2.6"]
    [org.clojure/test.check "0.5.8" :scope "test"]
		[org.clojure/clojure "1.8.0" :scope "provided"]])
