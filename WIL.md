# What I've learned

## Using HugSQL

### `-- :name` to define query name/type

- `-- :name save-messages! :! :n`
  - Creates query called `save-messages!`, and it ends with `!` since it mutates the data.
  - `:!` means that query is destructive.
  - `:n` means that query returns the number of affected rows.
- `-- :name get-messages :? :*`
  - `:?` means that query does a select.
  - `:*` means that query returns multiple rows.

## Testing

- Running `lein test` will read configs from `test-config.edn`
- You can run test in _watch-mode_ with `lein test-refresh` command.

## Running Standalone

```bash
# First, package the project into runnable JAR file
$ lein uberjar

# Then run with java cli, using database url
$ export DATABASE_URL="jdbc:h2:./guestbook_dev.db"
$ java -jar target/uberjar/guestbook.jar
```

## Adding dev dependency

Add `:scope "provided"`

```edn
[org.clojure/clojurescript "1.10.764" :scope "provided"]
```

## Build ClojureScript

```bash
# Build cljs
$ lein cljsbuild once
# Build cljs automatically when file changes
$ lein cljsbuild auto
```
