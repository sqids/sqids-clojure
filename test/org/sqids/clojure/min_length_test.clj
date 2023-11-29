(ns org.sqids.clojure.min-length-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is]]
    [org.sqids.clojure :as sut]))


(def min-length
  (count "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))


(defn make
  [min-length]
  (sut/sqids {:min-length min-length}))


(def sqids
  (make min-length))


(deftest simple-test
  (let [numbers [1 2 3]
        id      "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTM"]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))


(deftest incremental-test
  (let [numbers [1 2 3]]
    (doseq [[min-length id]
            [[6 "86Rf07"]
             [7 "86Rf07x"]
             [8 "86Rf07xd"]
             [9 "86Rf07xd4"]
             [10 "86Rf07xd4z"]
             [11 "86Rf07xd4zB"]
             [12 "86Rf07xd4zBm"]
             [13 "86Rf07xd4zBmi"]
             [(+ min-length 0) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTM"]
             [(+ min-length 1) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMy"]
             [(+ min-length 2) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMyf"]
             [(+ min-length 3) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMyf1"]]]
      (let [sqids (make min-length)]
        (is (= id (sut/encode sqids numbers)))
        (is (= numbers (sut/decode sqids id)))))))


(deftest incremental-numbers-test
  (doseq [[id & numbers]
          [["SvIzsqYMyQwI3GWgJAe17URxX8V924Co0DaTZLtFjHriEn5bPhcSkfmvOslpBu" 0 0]
           ["n3qafPOLKdfHpuNw3M61r95svbeJGk7aAEgYn4WlSjXURmF8IDqZBy0CT2VxQc" 0 1]
           ["tryFJbWcFMiYPg8sASm51uIV93GXTnvRzyfLleh06CpodJD42B7OraKtkQNxUZ" 0 2]
           ["eg6ql0A3XmvPoCzMlB6DraNGcWSIy5VR8iYup2Qk4tjZFKe1hbwfgHdUTsnLqE" 0 3]
           ["rSCFlp0rB2inEljaRdxKt7FkIbODSf8wYgTsZM1HL9JzN35cyoqueUvVWCm4hX" 0 4]
           ["sR8xjC8WQkOwo74PnglH1YFdTI0eaf56RGVSitzbjuZ3shNUXBrqLxEJyAmKv2" 0 5]
           ["uY2MYFqCLpgx5XQcjdtZK286AwWV7IBGEfuS9yTmbJvkzoUPeYRHr4iDs3naN0" 0 6]
           ["74dID7X28VLQhBlnGmjZrec5wTA1fqpWtK4YkaoEIM9SRNiC3gUJH0OFvsPDdy" 0 7]
           ["30WXpesPhgKiEI5RHTY7xbB1GnytJvXOl2p0AcUjdF6waZDo9Qk8VLzMuWrqCS" 0 8]
           ["moxr3HqLAK0GsTND6jowfZz3SUx7cQ8aC54Pl1RbIvFXmEJuBMYVeW9yrdOtin" 0 9]]]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))


(deftest min-lengths-test
  (doseq [min-length [0 1 5 10 min-length]]
    (let [sqids (make min-length)]
      (doseq [numbers [[0]
                       [0 0 0 0 0]
                       [1 2 3 4 5 6 7 8 9 10]
                       [100 200 300]
                       [1000 2000 30000]
                       [(long Integer/MAX_VALUE)]]]
        (let [id (sut/encode sqids numbers)]
          (is (<= min-length (count id)))
          (is (= numbers (sut/decode sqids id))))))))
