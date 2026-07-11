(ns openai.skills-test
  (:require [clojure.test :refer [deftest is]] [openai.skills :as skills])
  (:import (com.openai.models.skills SkillCreateParams SkillCreateParams$Files)))
(set! *warn-on-reflection* true)
(deftest translates-skill-files
  (let [^SkillCreateParams p (#'skills/->create-params {:files (.getBytes "skill" "UTF-8")})]
    (is (= [115 107 105 108 108]
           (vec (.readAllBytes
                 (.asInputStream ^SkillCreateParams$Files (.get (.files p)))))))))
