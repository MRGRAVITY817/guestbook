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

# When providing dev config
$ java -jar -Dconf=dev-config.edn target/uberjar/guestbook.jar
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
# Clean the distribution
$ lein clean
```

## Regeant (React.JS in Clojure)

- Can compose DOM tree with function components.
- Has built-in `atom` for managing states.
  - When we use atom for read-only purpose, we can conjoin `@`
    - (ex) `let [fields (r/atom {})]` -> `:value (:name @fields)`

## Reframe (Event Handler)

- Enables event-driven data management with Regeant.
- It has signal graph that contains 4 layers
  - Layer 1, app-db: The source data in map format.
  - Layer 2, extractors (direct subscription): Partial value extracted by keywords.
  - Layer 3, computation (derived subscription): Computed value using extracted values.
  - Layer 4, leaf views: The view function that renders computed value

### Features

- `rf/reg-event-fx`: Register(create)s new event
- `rf/reg-event-db`: Register db only event
- `rf/dispatch`: Add event into event-queue
- `rf/reg-sub`: Registers new subscription to an event
- `rf/subscribe`: Get reactive-value from subscription

## Coercion and Validation with Reitit

We need some way to convert EDN format into something else - like JSON. We use Reitit for this part.

## Muuntaja

_TODO: ADD NOTES_

## Shadow-cljs

A library that enables ClojureScript to Hot reload.  
Much better alternative than `cljsbuild`.

```bash
# Build and auto-reload cljs
$ npx shadow-cljs watch app
# Shadow-cljs repl, you can try DOM stuffs
$ npx shadow-cljs cljs-repl app
```
