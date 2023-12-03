(print (->> "./deps.edn"
            slurp
            read-string
            :aliases
            keys
            (apply str)))
