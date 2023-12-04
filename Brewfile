# frozen_string_literal: true

if File.exist?('/tmp/brew-bundle-github-actions')
  brew 'clojure', args: ['ignore-dependencies']
else
  brew 'clojure'

  tap 'borkdude/brew'
  brew 'borkdude/brew/clj-kondo'

  cask 'cljstyle'
end

brew 'pre-commit'
